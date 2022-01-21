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
package com.twitter.hpack

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util
import java.util.Random
import org.junit.Assert
import org.junit.Test

object HuffmanTest {
  @throws[IOException]
  private def roundTrip(encoder: HuffmanEncoder, decoder: HuffmanDecoder, s: String): Unit =
    roundTrip(encoder, decoder, s.getBytes)

  @throws[IOException]
  private def roundTrip(
      encoder: HuffmanEncoder,
      decoder: HuffmanDecoder,
      buf: Array[Byte],
  ): Unit = {
    val baos = new ByteArrayOutputStream
    val dos = new DataOutputStream(baos)
    encoder.encode(dos, buf)
    val actualBytes = decoder.decode(baos.toByteArray)
    Assert.assertTrue(util.Arrays.equals(buf, actualBytes))
  }
}

class HuffmanTest {
  @Test
  @throws[IOException]
  def testHuffman(): Unit = {
    val s = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    for (i <- 0 until s.length)
      roundTrip(s.substring(0, i))
    val random = new Random(123456789L)
    val buf = new Array[Byte](4096)
    random.nextBytes(buf)
    roundTrip(buf)
  }

  @Test(expected = classOf[IOException])
  @throws[IOException]
  def testDecodeEOS(): Unit = {
    val buf = new Array[Byte](4)
    for (i <- 0 until 4)
      buf(i) = 0xff.toByte
    Huffman.DECODER.decode(buf)
  }

  @Test(expected = classOf[IOException])
  @throws[IOException]
  def testDecodeIllegalPadding(): Unit = {
    val buf = new Array[Byte](1)
    buf(0) = 0x00 // '0', invalid padding

    Huffman.DECODER.decode(buf)
  }

  @Test // (expected = IOException.class) TODO(jpinner) fix me @throws[IOException]
  def testDecodeExtraPadding(): Unit = {
    val buf = new Array[Byte](2)
    buf(0) = 0x0f // '1', 'EOS'

    buf(1) = 0xff.toByte // 'EOS'

    Huffman.DECODER.decode(buf)
  }

  @throws[IOException]
  private def roundTrip(s: String): Unit =
    HuffmanTest.roundTrip(Huffman.ENCODER, Huffman.DECODER, s)

  @throws[IOException]
  private def roundTrip(buf: Array[Byte]): Unit =
    HuffmanTest.roundTrip(Huffman.ENCODER, Huffman.DECODER, buf)
}
