/*
 * Copyright 2022 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Copyright 2014 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.http4s.hpack

import cats.kernel.Eq
import cats.syntax.all._
import org.http4s.hpack.HpackUtil.ISO_8859_1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

import java.io.ByteArrayInputStream
import java.io.IOException

object DecoderTest {
  private val MAX_HEADER_SIZE = 8192
  private val MAX_HEADER_TABLE_SIZE = 4096

  private def hex(s: String) = Hex.encodeHexString(s.getBytes)

  private def getBytes(s: String) = s.getBytes(ISO_8859_1)
}

class MockHeaderListener extends HeaderListener {

  var headers: List[(Array[Byte], Array[Byte], Boolean)] = Nil

  def addHeader(name: Array[Byte], value: Array[Byte], sensitive: Boolean): Unit =
    headers = headers ::: (name, value, sensitive) :: Nil

  def reset() = headers = Nil

}

class DecoderTest {
  implicit val byteArrayEq: Eq[Array[Byte]] = Eq.by(_.toList)

  private var decoder: Decoder = null
  private var mockListener: MockHeaderListener = null

  @throws[IOException]
  private def decode(encoded: String): Unit = {
    val b = Hex.decodeHex(encoded.toCharArray)
    decoder.decode(new ByteArrayInputStream(b), mockListener)
  }

  @Before def setUp(): Unit = {
    decoder = new Decoder(DecoderTest.MAX_HEADER_SIZE, DecoderTest.MAX_HEADER_TABLE_SIZE)
    mockListener = new MockHeaderListener
  }

  @Test
  @throws[IOException]
  def testIncompleteIndex(): Unit = { // Verify incomplete indices are unread
    val compressed = Hex.decodeHex("FFF0".toCharArray)
    val in = new ByteArrayInputStream(compressed)
    decoder.decode(in, mockListener)
    assertEquals(1, in.available)
    decoder.decode(in, mockListener)
    assertEquals(1, in.available)
  }

  @Test(expected = classOf[IOException])
  @throws[IOException]
  def testUnusedIndex(): Unit = // Index 0 is not used
    decode("80")

  @Test(expected = classOf[IOException])
  @throws[IOException]
  def testIllegalIndex(): Unit = // Index larger than the header table
    decode("FF00")

  @Test(expected = classOf[IOException])
  @throws[IOException]
  def testInsidiousIndex(): Unit = // Insidious index so the last shift causes sign overflow
    decode("FF8080808008")

  @Test
  @throws[Exception]
  def testDynamicTableSizeUpdate(): Unit = {
    decode("20")
    assertEquals(0, decoder.getMaxHeaderTableSize)
    decode("3FE11F")
    assertEquals(4096, decoder.getMaxHeaderTableSize)
  }

  @Test
  @throws[Exception]
  def testDynamicTableSizeUpdateRequired(): Unit = {
    decoder.setMaxHeaderTableSize(32)
    decode("3F00")
    assertEquals(31, decoder.getMaxHeaderTableSize)
  }

  @Test(expected = classOf[IOException])
  @throws[Exception]
  def testIllegalDynamicTableSizeUpdate()
      : Unit = // max header table size = MAX_HEADER_TABLE_SIZE + 1
    decode("3FE21F")

  @Test(expected = classOf[IOException])
  @throws[IOException]
  def testInsidiousMaxDynamicTableSize(): Unit = // max header table size sign overflow
    decode("3FE1FFFFFF07")

  @Test
  @throws[Exception]
  def testReduceMaxDynamicTableSize(): Unit = {
    decoder.setMaxHeaderTableSize(0)
    assertEquals(0, decoder.getMaxHeaderTableSize)
    decode("2081")
  }

  @Test(expected = classOf[IOException])
  @throws[IOException]
  def testDynamicTableSizeUpdateAfterTheBeginningOfTheBlock(): Unit =
    decode("8120")

  @Test(expected = classOf[IOException])
  @throws[Exception]
  def testDynamicTableSizeUpdateAfterTheBeginningOfTheBlockLong(): Unit =
    decode("813FE11F")

  @Test(expected = classOf[IOException])
  @throws[Exception]
  def testTooLargeDynamicTableSizeUpdate(): Unit = {
    decoder.setMaxHeaderTableSize(0)
    assertEquals(0, decoder.getMaxHeaderTableSize)
    decode("21") // encoder max header table size not small enough

  }

  @Test(expected = classOf[IOException])
  @throws[Exception]
  def testMissingDynamicTableSizeUpdate(): Unit = {
    decoder.setMaxHeaderTableSize(0)
    assertEquals(0, decoder.getMaxHeaderTableSize)
    decode("81")
  }

  @Test(expected = classOf[IOException])
  @throws[Exception]
  def testLiteralWithIncrementalIndexingWithEmptyName(): Unit =
    decode("000005" + DecoderTest.hex("value"))

  @Test
  @throws[Exception]
  def testLiteralWithIncrementalIndexingCompleteEviction(): Unit = { // Verify indexed host header
    decode("4004" + DecoderTest.hex("name") + "05" + DecoderTest.hex("value"))
    assertTrue(
      mockListener.headers === List(
        (
          DecoderTest.getBytes("name"),
          DecoderTest.getBytes("value"),
          false,
        )
      )
    )
    assertFalse(decoder.endHeaderBlock)
    mockListener.reset()
    var sb = new StringBuilder
    for (i <- 0 until 4096)
      sb.append("a")
    val value = sb.toString
    sb = new StringBuilder
    sb.append("417F811F")
    for (i <- 0 until 4096)
      sb.append("61") // 'a'

    decode(sb.toString)
    assertTrue(
      mockListener.headers ===
        List(
          (
            DecoderTest.getBytes(":authority"),
            DecoderTest.getBytes(value),
            false,
          )
        )
    )
    assertFalse(decoder.endHeaderBlock)
    // Verify next header is inserted at index 62
    decode("4004" + DecoderTest.hex("name") + "05" + DecoderTest.hex("value") + "BE")
    assertEquals(
      mockListener.headers
        .count(
          _ ===
            Tuple3(
              DecoderTest.getBytes("name"),
              DecoderTest.getBytes("value"),
              false,
            )
        )
        .toLong,
      2,
    )
  }

  @Test
  @throws[Exception]
  def testLiteralWithIncrementalIndexingWithLargeName()
      : Unit = { // Ignore header name that exceeds max header size
    val sb = new StringBuilder
    sb.append("407F817F")
    for (i <- 0 until 16384)
      sb.append("61")
    sb.append("00")
    decode(sb.toString)
    assertTrue(mockListener.headers.isEmpty)
    // Verify header block is reported as truncated
    assertTrue(decoder.endHeaderBlock)
    decode("4004" + DecoderTest.hex("name") + "05" + DecoderTest.hex("value") + "BE")
    assertTrue(
      mockListener.headers ===
        List.fill(2)(
          (
            DecoderTest.getBytes("name"),
            DecoderTest.getBytes("value"),
            false,
          )
        )
    )
  }

  @Test
  @throws[Exception]
  def testLiteralWithIncrementalIndexingWithLargeValue()
      : Unit = { // Ignore header that exceeds max header size
    val sb = new StringBuilder
    sb.append("4004")
    sb.append(DecoderTest.hex("name"))
    sb.append("7F813F")
    for (i <- 0 until 8192)
      sb.append("61")
    decode(sb.toString)
    assertTrue(mockListener.headers.isEmpty)
    assertTrue(decoder.endHeaderBlock)
    decode("4004" + DecoderTest.hex("name") + "05" + DecoderTest.hex("value") + "BE")
    assertTrue(
      mockListener.headers ===
        List.fill(2)(
          (
            DecoderTest.getBytes("name"),
            DecoderTest.getBytes("value"),
            false,
          )
        )
    )
  }

  @Test(expected = classOf[IOException])
  @throws[Exception]
  def testLiteralWithoutIndexingWithEmptyName(): Unit =
    decode("000005" + DecoderTest.hex("value"))

  @Test(expected = classOf[IOException])
  @throws[Exception]
  def testLiteralWithoutIndexingWithLargeName(): Unit = {
    val sb = new StringBuilder
    sb.append("007F817F")
    for (i <- 0 until 16384)
      sb.append("61")
    sb.append("00")
    decode(sb.toString)
    assertTrue(mockListener.headers.isEmpty)
    assertTrue(decoder.endHeaderBlock)
    // Verify table is unmodified
    decode("BE")
  }

  @Test(expected = classOf[IOException])
  @throws[Exception]
  def testLiteralWithoutIndexingWithLargeValue(): Unit = {
    val sb = new StringBuilder
    sb.append("0004")
    sb.append(DecoderTest.hex("name"))
    sb.append("7F813F")
    for (i <- 0 until 8192)
      sb.append("61")
    decode(sb.toString)
    assertTrue(mockListener.headers.isEmpty)
    assertTrue(decoder.endHeaderBlock)
    decode("BE")
  }

  @Test(expected = classOf[IOException])
  @throws[Exception]
  def testLiteralNeverIndexedWithEmptyName(): Unit =
    decode("100005" + DecoderTest.hex("value"))

  @Test(expected = classOf[IOException])
  @throws[Exception]
  def testLiteralNeverIndexedWithLargeName(): Unit = {
    val sb = new StringBuilder
    sb.append("107F817F")
    for (i <- 0 until 16384)
      sb.append("61")
    sb.append("00")
    decode(sb.toString)
    assertTrue(mockListener.headers.isEmpty)
    assertTrue(decoder.endHeaderBlock)
    decode("BE")
  }

  @Test(expected = classOf[IOException])
  @throws[Exception]
  def testLiteralNeverIndexedWithLargeValue(): Unit = {
    val sb = new StringBuilder
    sb.append("1004")
    sb.append(DecoderTest.hex("name"))
    sb.append("7F813F")
    for (i <- 0 until 8192)
      sb.append("61")
    decode(sb.toString)
    assertTrue(mockListener.headers.isEmpty)
    assertTrue(decoder.endHeaderBlock)
    decode("BE")
  }
}
