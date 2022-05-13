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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import HuffmanDecoder._

private[http4s] final class HuffmanDecoder(root: Node) {

  private[this] val EOS_DECODED = new IOException("EOS Decoded");
  private[this] val INVALID_PADDING = new IOException("Invalid Padding");

  /** Creates a new Huffman decoder with the specified Huffman coding.
    * @param codes   the Huffman codes indexed by symbol
    * @param lengths the length of each Huffman code
    */
  def this(codes: Array[Int], lengths: Array[Byte]) =
    this {
      if (codes.length != 257 || codes.length != lengths.length) {
        throw new IllegalArgumentException("invalid Huffman coding");
      }
      buildTree(codes, lengths);
    }

  /** Decompresses the given Huffman coded string literal.
    * @param  buf the string literal to be decoded
    * @return the output stream for the compressed data
    * @throws IOException if an I/O error occurs. In particular,
    *         an <code>IOException</code> may be thrown if the
    *         output stream has been closed.
    */
  @throws[IOException]
  def decode(buf: Array[Byte]): Array[Byte] = {
    val baos = new ByteArrayOutputStream;

    var node = root;
    var current = 0;
    var bits = 0;
    var i = 0
    while (i < buf.length) {
      val b = buf(i) & 0xff;
      current = current << 8 | b;
      bits += 8;
      while (bits >= 8) {
        val c = current >>> bits - 8 & 0xff;
        node = node.children(c);
        bits -= node.bits;
        if (node.isTerminal()) {
          if (node.symbol == HpackUtil.HUFFMAN_EOS) {
            throw EOS_DECODED;
          }
          baos.write(node.symbol);
          node = root;
        }
      }
      i += 1
    }

    var break = false
    while (!break && bits > 0) {
      val c = current << 8 - bits & 0xff;
      node = node.children(c);
      if (node.isTerminal() && node.bits <= bits) {
        bits -= node.bits;
        baos.write(node.symbol);
        node = root;
      } else {
        break = true;
      }
    }

    // Section 5.2. String Literal Representation
    // Padding not corresponding to the most significant bits of the code
    // for the EOS symbol (0xFF) MUST be treated as a decoding error.
    val mask = (1 << bits) - 1;
    if ((current & mask) != mask) {
      throw INVALID_PADDING;
    }

    return baos.toByteArray();
  }

}

private[http4s] object HuffmanDecoder {

  private[hpack] final class Node(
      val symbol: Int, // terminal nodes have a symbol
      val bits: Int, // number of bits matched by the node
      val children: Array[Node], // internal nodes have children
  ) {

    /** Construct an internal node
      */
    private[HuffmanDecoder] def this() =
      this(0, 8, new Array[Node](256))

    /** Construct a terminal node
      * @param symbol the symbol the node represents
      * @param bits   the number of bits matched by this node
      */
    private[HuffmanDecoder] def this(symbol: Int, bits: Int) {
      this(symbol, bits, null)
      assert(bits > 0 && bits <= 8);
    }

    private[HuffmanDecoder] def isTerminal(): Boolean =
      return children == null;
  }

  private def buildTree(codes: Array[Int], lengths: Array[Byte]): Node = {
    val root = new Node;
    var i = 0
    while (i < codes.length) {
      insert(root, i, codes(i), lengths(i));
      i += 1
    }
    return root;
  }

  private[this] def insert(root: Node, symbol: Int, code: Int, _length: Byte): Unit = {
    var length = _length
    // traverse tree using the most significant bytes of code
    var current = root;
    while (length > 8) {
      if (current.isTerminal()) {
        throw new IllegalStateException("invalid Huffman code: prefix not unique");
      }
      length = (length - 8).toByte;
      val i = code >>> length & 0xff;
      if (current.children(i) == null) {
        current.children(i) = new Node;
      }
      current = current.children(i);
    }

    val terminal = new Node(symbol, length);
    val shift = 8 - length;
    val start = code << shift & 0xff;
    val end = 1 << shift;
    var i = start
    while (i < start + end) {
      current.children(i) = terminal;
      i += 1
    }
  }
}
