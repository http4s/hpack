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
import java.util.Arrays;

import org.http4s.hpack.HpackUtil.IndexType;

private[http4s] final class Encoder(
    // for testing
    useIndexing: Boolean,
    forceHuffmanOn: Boolean,
    forceHuffmanOff: Boolean,
) {

  private[this] val BUCKET_SIZE = 17;
  private val EMPTY = Array.emptyByteArray;

  // a linked hash map of header fields
  private val headerFields: Array[HeaderEntry] = new Array[HeaderEntry](BUCKET_SIZE);
  private val head: HeaderEntry = new HeaderEntry(-1, EMPTY, EMPTY, Integer.MAX_VALUE, null);
  private[hpack] var size: Int = _;
  private var capacity: Int = _;

  /** Constructor for testing only.
    */
  def this(
      maxHeaderTableSize: Int,
      useIndexing: Boolean,
      forceHuffmanOn: Boolean,
      forceHuffmanOff: Boolean,
  ) {
    this(useIndexing, forceHuffmanOn, forceHuffmanOff)
    if (maxHeaderTableSize < 0) {
      throw new IllegalArgumentException("Illegal Capacity: " + maxHeaderTableSize);
    }
    this.capacity = maxHeaderTableSize;
    head.before = head;
    head.after = head;
  }

  /** Creates a new encoder.
    */
  def this(maxHeaderTableSize: Int) =
    this(maxHeaderTableSize, true, false, false);

  /** Encode the header field into the header block.
    */
  @throws[IOException]
  def encodeHeader(
      out: OutputStream,
      name: Array[Byte],
      value: Array[Byte],
      sensitive: Boolean,
  ): Unit = {

    // If the header value is sensitive then it must never be indexed
    if (sensitive) {
      val nameIndex = getNameIndex(name);
      encodeLiteral(out, name, value, IndexType.NEVER, nameIndex);
      return;
    }

    // If the peer will only use the static table
    if (capacity == 0) {
      val staticTableIndex = StaticTable.getIndex(name, value);
      if (staticTableIndex == -1) {
        val nameIndex = StaticTable.getIndex(name);
        encodeLiteral(out, name, value, IndexType.NONE, nameIndex);
      } else {
        encodeInteger(out, 0x80, 7, staticTableIndex);
      }
      return;
    }

    val headerSize = HeaderField.sizeOf(name, value);

    // If the headerSize is greater than the max table size then it must be encoded literally
    if (headerSize > capacity) {
      val nameIndex = getNameIndex(name);
      encodeLiteral(out, name, value, IndexType.NONE, nameIndex);
      return;
    }

    val headerField = getEntry(name, value);
    if (headerField != null) {
      val index = getIndex(headerField.index) + StaticTable.length;
      // Section 6.1. Indexed Header Field Representation
      encodeInteger(out, 0x80, 7, index);
    } else {
      val staticTableIndex = StaticTable.getIndex(name, value);
      if (staticTableIndex != -1) {
        // Section 6.1. Indexed Header Field Representation
        encodeInteger(out, 0x80, 7, staticTableIndex);
      } else {
        val nameIndex = getNameIndex(name);
        if (useIndexing) {
          ensureCapacity(headerSize);
        }
        val indexType = if (useIndexing) IndexType.INCREMENTAL else IndexType.NONE;
        encodeLiteral(out, name, value, indexType, nameIndex);
        if (useIndexing) {
          add(name, value);
        }
      }
    }
  }

  /** Set the maximum table size.
    */
  @throws[IOException]
  def setMaxHeaderTableSize(out: OutputStream, maxHeaderTableSize: Int): Unit = {
    if (maxHeaderTableSize < 0) {
      throw new IllegalArgumentException("Illegal Capacity: " + maxHeaderTableSize);
    }
    if (capacity == maxHeaderTableSize) {
      return;
    }
    capacity = maxHeaderTableSize;
    ensureCapacity(0);
    encodeInteger(out, 0x20, 5, maxHeaderTableSize);
  }

  /** Return the maximum table size.
    */
  def getMaxHeaderTableSize(): Int =
    return capacity;

  /** Encode integer according to Section 5.1.
    */
  @throws[IOException]
  private[this] def encodeInteger(out: OutputStream, mask: Int, n: Int, i: Int): Unit = {
    if (n < 0 || n > 8) {
      throw new IllegalArgumentException("N: " + n);
    }
    val nbits = 0xff >>> (8 - n);
    if (i < nbits) {
      out.write(mask | i);
    } else {
      out.write(mask | nbits);
      var length = i - nbits;
      while (true)
        if ((length & ~0x7f) == 0) {
          out.write(length);
          return;
        } else {
          out.write((length & 0x7f) | 0x80);
          length >>>= 7;
        }
    }
  }

  /** Encode string literal according to Section 5.2.
    */
  @throws[IOException]
  private[this] def encodeStringLiteral(out: OutputStream, string: Array[Byte]): Unit = {
    val huffmanLength = Huffman.ENCODER.getEncodedLength(string);
    if ((huffmanLength < string.length && !forceHuffmanOff) || forceHuffmanOn) {
      encodeInteger(out, 0x80, 7, huffmanLength);
      Huffman.ENCODER.encode(out, string);
    } else {
      encodeInteger(out, 0x00, 7, string.length);
      out.write(string, 0, string.length);
    }
  }

  /** Encode literal header field according to Section 6.2.
    */
  @throws[IOException]
  private[this] def encodeLiteral(
      out: OutputStream,
      name: Array[Byte],
      value: Array[Byte],
      indexType: IndexType,
      nameIndex: Int,
  ): Unit = {
    import IndexType._
    var mask: Int = 0;
    var prefixBits: Int = 0;
    indexType match {
      case INCREMENTAL =>
        mask = 0x40;
        prefixBits = 6;
      case NONE =>
        mask = 0x00;
        prefixBits = 4;
      case NEVER =>
        mask = 0x10;
        prefixBits = 4;
    }
    encodeInteger(out, mask, prefixBits, if (nameIndex == -1) 0 else nameIndex);
    if (nameIndex == -1) {
      encodeStringLiteral(out, name);
    }
    encodeStringLiteral(out, value);
  }

  private def getNameIndex(name: Array[Byte]): Int = {
    var index = StaticTable.getIndex(name);
    if (index == -1) {
      index = getIndex(name);
      if (index >= 0) {
        index += StaticTable.length;
      }
    }
    return index;
  }

  /** Ensure that the dynamic table has enough room to hold 'headerSize' more bytes.
    * Removes the oldest entry from the dynamic table until sufficient space is available.
    */
  @throws[IOException]
  private[this] def ensureCapacity(headerSize: Int): Unit =
    while (size + headerSize > capacity) {
      val index = length();
      if (index == 0) {
        return;
      }
      remove();
    }

  /** Return the number of header fields in the dynamic table.
    * Exposed for testing.
    */
  private[hpack] def length(): Int =
    return if (size == 0) 0 else (head.after.index - head.before.index + 1);

  /** Return the header field at the given index.
    * Exposed for testing.
    */
  private[hpack] def getHeaderField(_index: Int): HeaderField = {
    var index = _index;
    var entry = head;
    while (index >= 0) {
      entry = entry.before;
      index -= 1
    }
    return entry;
  }

  /** Returns the header entry with the lowest index value for the header field.
    * Returns null if header field is not in the dynamic table.
    */
  private[this] def getEntry(name: Array[Byte], value: Array[Byte]): HeaderEntry = {
    if (length() == 0 || name == null || value == null) {
      return null;
    }
    val h = hash(name);
    val i = index(h);
    var e = headerFields(i)
    while (e != null) {
      if (
        e.hash == h &&
        HpackUtil.equals(name, e.name) &&
        HpackUtil.equals(value, e.value)
      ) {
        return e;
      }
      e = e.next
    }
    return null;
  }

  /** Returns the lowest index value for the header field name in the dynamic table.
    * Returns -1 if the header field name is not in the dynamic table.
    */
  private[this] def getIndex(name: Array[Byte]): Int = {
    if (length() == 0 || name == null) {
      return -1;
    }
    var h = hash(name);
    var i = this.index(h);
    var index = -1;
    var e = headerFields(i);
    while (e != null) {
      if (e.hash == h && HpackUtil.equals(name, e.name)) {
        index = e.index;
        return getIndex(index);
      }
      e = e.next
    }
    return getIndex(index);
  }

  /** Compute the index into the dynamic table given the index in the header entry.
    */
  private[this] def getIndex(index: Int): Int = {
    if (index == -1) {
      return index;
    }
    return index - head.before.index + 1;
  }

  /** Add the header field to the dynamic table.
    * Entries are evicted from the dynamic table until the size of the table
    * and the new header field is less than the table's capacity.
    * If the size of the new entry is larger than the table's capacity,
    * the dynamic table will be cleared.
    */
  private[this] def add(_name: Array[Byte], _value: Array[Byte]): Unit = {
    var name = _name
    var value = _value

    var headerSize = HeaderField.sizeOf(name, value);

    // Clear the table if the header field size is larger than the capacity.
    if (headerSize > capacity) {
      clear();
      return;
    }

    // Evict oldest entries until we have enough capacity.
    while (size + headerSize > capacity)
      remove();

    // Copy name and value that modifications of original do not affect the dynamic table.
    name = Arrays.copyOf(name, name.length);
    value = Arrays.copyOf(value, value.length);

    val h = hash(name);
    val i = index(h);
    val old = headerFields(i);
    val e = new HeaderEntry(h, name, value, head.before.index - 1, old);
    headerFields(i) = e;
    e.addBefore(head);
    size += headerSize;
  }

  /** Remove and return the oldest header field from the dynamic table.
    */
  private[this] def remove(): HeaderField = {
    if (size == 0) {
      return null;
    }
    val eldest = head.after;
    val h = eldest.hash;
    val i = index(h);
    var prev = headerFields(i);
    var e = prev;
    while (e != null) {
      val next = e.next;
      if (e == eldest) {
        if (prev == eldest) {
          headerFields(i) = next;
        } else {
          prev.next = next;
        }
        eldest.remove();
        size -= eldest.size();
        return eldest;
      }
      prev = e;
      e = next;
    }
    return null;
  }

  /** Remove all entries from the dynamic table.
    */
  private[this] def clear(): Unit = {
    Arrays.fill(headerFields.asInstanceOf[Array[AnyRef]], null);
    head.before = head;
    head.after = head;
    this.size = 0;
  }

  /** Returns the hash code for the given header field name.
    */
  private[this] def hash(name: Array[Byte]): Int = {
    var h = 0;
    var i = 0
    while (i < name.length) {
      h = 31 * h + name(i);
      i += 1
    }
    if (h > 0) {
      return h;
    } else if (h == Integer.MIN_VALUE) {
      return Integer.MAX_VALUE;
    } else {
      return -h;
    }
  }

  /** Returns the index into the hash table for the hash code h.
    */
  private[this] def index(h: Int): Int =
    return h % BUCKET_SIZE;

  /** A linked hash map HeaderField entry.
    */
  private[this] class HeaderEntry(
      val hash: Int,
      name: Array[Byte],
      value: Array[Byte],
      val index: Int,
      var next: HeaderEntry,
  ) extends HeaderField(name, value) {
    // These fields comprise the doubly linked list used for iteration.
    var before: HeaderEntry = _
    var after: HeaderEntry = _

    /** Removes this entry from the linked list.
      */
    def remove(): Unit = {
      before.after = after;
      after.before = before;
      before = null; // null reference to prevent nepotism with generational GC.
      after = null; // null reference to prevent nepotism with generational GC.
      next = null; // null reference to prevent nepotism with generational GC.
    }

    /** Inserts this entry before the specified existing entry in the list.
      */
    def addBefore(existingEntry: HeaderEntry): Unit = {
      after = existingEntry;
      before = existingEntry.before;
      before.after = this;
      after.before = this;
    }
  }
}
