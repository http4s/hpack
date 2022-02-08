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

import io.circe

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util

case class TestCase(
    maxHeaderTableSize: Option[Int],
    useIndexing: Option[Boolean],
    sensitiveHeaders: Option[Boolean],
    forceHuffmanOn: Option[Boolean],
    forceHuffmanOff: Option[Boolean],
    headerBlocks: List[TestCase.HeaderBlock],
) {

  def testCompress() = {
    val encoder = createEncoder();

    headerBlocks.foreach { headerBlock =>
      val actual = encode(
        encoder,
        headerBlock.headers,
        headerBlock.maxHeaderTableSize.getOrElse(-1),
        sensitiveHeaders.getOrElse(false),
      );

      if (!util.Arrays.equals(actual, headerBlock.encodedBytes)) {
        throw new AssertionError(
          "\nEXPECTED:\n" + headerBlock.getEncodedStr +
            "\nACTUAL:\n" + Hex.encodeHexString(actual)
        );
      }

      val actualDynamicTable = List.newBuilder[HeaderField];
      for (index <- 0 until encoder.length())
        actualDynamicTable += encoder.getHeaderField(index);

      val expectedDynamicTable = headerBlock.dynamicTable;

      if (!expectedDynamicTable.equals(actualDynamicTable.result())) {
        throw new AssertionError(
          "\nEXPECTED DYNAMIC TABLE:\n" + expectedDynamicTable +
            "\nACTUAL DYNAMIC TABLE:\n" + actualDynamicTable
        );
      }

      if (headerBlock.tableSize != encoder.size) {
        throw new AssertionError(
          "\nEXPECTED TABLE SIZE: " + headerBlock.tableSize +
            "\n ACTUAL TABLE SIZE : " + encoder.size
        );
      }
    }
  }

  def testDecompress() = {
    val decoder = createDecoder();

    headerBlocks.foreach { headerBlock =>
      val actualHeaders = decode(decoder, headerBlock.encodedBytes);

      val expectedHeaders = new util.ArrayList[HeaderField]
      headerBlock.headers.foreach { h =>
        expectedHeaders.add(new HeaderField(h.name, h.value));
      }

      if (!expectedHeaders.equals(actualHeaders)) {
        throw new AssertionError(
          "\nEXPECTED:\n" + expectedHeaders +
            "\nACTUAL:\n" + actualHeaders
        );
      }

      val actualDynamicTable = List.newBuilder[HeaderField]
      for (index <- 0 until decoder.length())
        actualDynamicTable += decoder.getHeaderField(index);

      val expectedDynamicTable = headerBlock.dynamicTable;

      if (!expectedDynamicTable.equals(actualDynamicTable)) {
        throw new AssertionError(
          "\nEXPECTED DYNAMIC TABLE:\n" + expectedDynamicTable +
            "\nACTUAL DYNAMIC TABLE:\n" + actualDynamicTable
        );
      }

      if (headerBlock.tableSize != decoder.size()) {
        throw new AssertionError(
          "\nEXPECTED TABLE SIZE: " + headerBlock.tableSize +
            "\n ACTUAL TABLE SIZE : " + decoder.size()
        );
      }
    }
  }

  private def createEncoder() =
    new Encoder(
      maxHeaderTableSize.getOrElse(Int.MaxValue),
      useIndexing.getOrElse(true),
      forceHuffmanOn.getOrElse(false),
      forceHuffmanOff.getOrElse(false),
    );

  private def createDecoder() =
    new Decoder(8192, maxHeaderTableSize.getOrElse(Int.MaxValue));

  @throws[IOException]
  private def encode(
      encoder: Encoder,
      headers: List[HeaderField],
      maxHeaderTableSize: Int,
      sensitive: Boolean,
  ) = {
    val baos = new ByteArrayOutputStream();

    if (maxHeaderTableSize != -1) {
      encoder.setMaxHeaderTableSize(baos, maxHeaderTableSize);
    }

    headers.foreach { e =>
      encoder.encodeHeader(baos, e.name, e.value, sensitive);
    }

    baos.toByteArray();
  }

  @throws[IOException]
  private def decode(decoder: Decoder, expected: Array[Byte]) = {
    val headers = new util.ArrayList[HeaderField]();
    val listener = new TestHeaderListener(headers);
    decoder.decode(new ByteArrayInputStream(expected), listener);
    decoder.endHeaderBlock();
    headers;
  }

}

object TestCase extends TestCasePlatform {

  implicit def decoder: circe.Decoder[TestCase] =
    circe.Decoder.forProduct6(
      "max_header_table_size",
      "use_indexing",
      "sensitive_headers",
      "force_huffman_on",
      "force_huffman_off",
      "header_blocks",
    )(TestCase(_, _, _, _, _, _))

  case class HeaderBlock(
      maxHeaderTableSize: Option[Int],
      encoded: List[String],
      headers: List[HeaderField],
      dynamicTable: List[HeaderField],
      tableSize: Int,
  ) {
    def getEncodedStr = encoded.mkString.replace(" ", "")
    lazy val encodedBytes = Hex.decodeHex(getEncodedStr.toCharArray())
  }

  implicit def headerBlockDecoder: circe.Decoder[HeaderBlock] =
    circe.Decoder.forProduct5(
      "max_header_table_size",
      "encoded",
      "headers",
      "dynamic_table",
      "table_size",
    )(HeaderBlock(_, _, _, _, _))

  implicit def headerFieldDecoder: circe.Decoder[HeaderField] =
    circe.Decoder
      .decodeMap[String, String]
      .map(_.toList)
      .emap {
        case List((name, value)) => Right(new HeaderField(name, value))
        case _ => Left("HeaderField object must have exactly 1 entry")
      }

}
