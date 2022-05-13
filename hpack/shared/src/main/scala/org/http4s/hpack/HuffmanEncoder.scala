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
package org.http4s.hpack;

import java.io.IOException;
import java.io.OutputStream;

/** Creates a new Huffman encoder with the specified Huffman coding.
  * @param codes   the Huffman codes indexed by symbol
  * @param lengths the length of each Huffman code
  */
private[http4s] final class HuffmanEncoder(codes: Array[Int], lengths: Array[Byte]) {

  /** Compresses the input string literal using the Huffman coding.
    * @param  out  the output stream for the compressed data
    * @param  data the string literal to be Huffman encoded
    * @throws IOException if an I/O error occurs.
    * @see    org.http4s.hpack.HuffmanEncoder#encode(OutputStream, byte[], int, int)
    */
  @throws[IOException]
  def encode(out: OutputStream, data: Array[Byte]): Unit =
    encode(out, data, 0, data.length);

  /** Compresses the input string literal using the Huffman coding.
    * @param  out  the output stream for the compressed data
    * @param  data the string literal to be Huffman encoded
    * @param  off  the start offset in the data
    * @param  len  the number of bytes to encode
    * @throws IOException if an I/O error occurs. In particular,
    *         an <code>IOException</code> may be thrown if the
    *         output stream has been closed.
    */
  @throws[IOException]
  def encode(out: OutputStream, data: Array[Byte], off: Int, len: Int): Unit = {
    if (out == null) {
      throw new NullPointerException("out");
    } else if (data == null) {
      throw new NullPointerException("data");
    } else if (
      off < 0 || len < 0 || off + len < 0 || off > data.length || off + len > data.length
    ) {
      throw new IndexOutOfBoundsException;
    } else if (len == 0) {
      return;
    }

    var current = 0L;
    var n = 0;

    var i = 0
    while (i < len) {
      val b = data(off + i) & 0xff;
      val code = codes(b);
      val nbits = lengths(b);

      current <<= nbits;
      current |= code;
      n += nbits;

      while (n >= 8) {
        n -= 8;
        out.write((current >> n).toInt);
      }
      i += 1
    }

    if (n > 0) {
      current <<= 8 - n;
      current |= 0xff >>> n; // this should be EOS symbol
      out.write(current.toInt);
    }
  }

  /** Returns the number of bytes required to Huffman encode the input string literal.
    * @param  data the string literal to be Huffman encoded
    * @return the number of bytes required to Huffman encode <code>data</code>
    */
  def getEncodedLength(data: Array[Byte]): Int = {
    if (data == null) {
      throw new NullPointerException("data");
    }
    var len = 0L;
    var i = 0
    while (i < data.length) {
      val b = data(i)
      len += lengths(b & 0xff);
      i += 1
    }
    return (len + 7 >> 3).toInt;
  }
}
