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

import org.http4s.hpack.HeaderField.HEADER_ENTRY_OVERHEAD
import org.http4s.hpack.HpackUtil.IndexType

import java.io.IOException
import java.io.InputStream;

private[http4s] final class Decoder(dynamicTable: DynamicTable) {

  private[this] val DECOMPRESSION_EXCEPTION =
    new IOException("decompression failure");
  private[this] val ILLEGAL_INDEX_VALUE =
    new IOException("illegal index value");
  private[this] val INVALID_MAX_DYNAMIC_TABLE_SIZE =
    new IOException("invalid max dynamic table size");
  private[this] val MAX_DYNAMIC_TABLE_SIZE_CHANGE_REQUIRED =
    new IOException("max dynamic table size change required");
  private[this] val UNEXPECTED_DYNAMIC_TABLE_SIZE_UPDATE =
    new IOException(
      "Dynamic table size update must happen "
        + "at the beginning of the header block"
    )

  private[this] val EMPTY = Array.emptyByteArray;

  private[this] var maxHeaderSize: Int = _
  private[this] var maxDynamicTableSize: Int = _
  private[this] var encoderMaxDynamicTableSize: Int = _
  private[this] var maxDynamicTableSizeChangeRequired: Boolean = _

  private[this] var headerSize: Long = _
  private[this] var state: State = _
  private[this] var indexType: IndexType = _
  private[this] var index: Int = _
  private[this] var huffmanEncoded: Boolean = _
  private[this] var skipLength: Int = _
  private[this] var nameLength: Int = _
  private[this] var valueLength: Int = _
  private[this] var name: Array[Byte] = _

  private sealed abstract class State
  private object State {
    case object READ_HEADER_REPRESENTATION extends State
    case object READ_MAX_DYNAMIC_TABLE_SIZE extends State
    case object READ_INDEXED_HEADER extends State
    case object READ_INDEXED_HEADER_NAME extends State
    case object READ_LITERAL_HEADER_NAME_LENGTH_PREFIX extends State
    case object READ_LITERAL_HEADER_NAME_LENGTH extends State
    case object READ_LITERAL_HEADER_NAME extends State
    case object SKIP_LITERAL_HEADER_NAME extends State
    case object READ_LITERAL_HEADER_VALUE_LENGTH_PREFIX extends State
    case object READ_LITERAL_HEADER_VALUE_LENGTH extends State
    case object READ_LITERAL_HEADER_VALUE extends State
    case object SKIP_LITERAL_HEADER_VALUE extends State
  }

  /** Creates a new decoder.
    */
  def this(maxHeaderSize: Int, maxHeaderTableSize: Int) = {
    this(new DynamicTable(maxHeaderTableSize));
    this.maxHeaderSize = maxHeaderSize;
    maxDynamicTableSize = maxHeaderTableSize;
    encoderMaxDynamicTableSize = maxHeaderTableSize;
    maxDynamicTableSizeChangeRequired = false;
    reset();
  }

  private def reset(): Unit = {
    headerSize = 0;
    state = State.READ_HEADER_REPRESENTATION;
    indexType = IndexType.NONE;
  }

  /** Decode the header block into header fields.
    */
  @throws[IOException]
  def decode(in: InputStream, headerListener: HeaderListener): Unit = {
    var seenOnlyDynamicTableSizeUpdate = true

    while (in.available() > 0) {
      var b: Byte = 0
      import State._
      state match {
        case READ_HEADER_REPRESENTATION =>
          b = in.read().toByte;
          if (maxDynamicTableSizeChangeRequired && (b & 0xe0) != 0x20) {
            // Encoder MUST signal maximum dynamic table size change
            throw MAX_DYNAMIC_TABLE_SIZE_CHANGE_REQUIRED;
          }
          if (b < 0) {
            // Indexed Header Field
            seenOnlyDynamicTableSizeUpdate = false
            index = b & 0x7f;
            if (index == 0) {
              throw ILLEGAL_INDEX_VALUE;
            } else if (index == 0x7f) {
              state = State.READ_INDEXED_HEADER;
            } else {
              indexHeader(index, headerListener);
            }
          } else if ((b & 0x40) == 0x40) {
            // Literal Header Field with Incremental Indexing
            seenOnlyDynamicTableSizeUpdate = false
            indexType = IndexType.INCREMENTAL;
            index = b & 0x3f;
            if (index == 0) {
              state = State.READ_LITERAL_HEADER_NAME_LENGTH_PREFIX;
            } else if (index == 0x3f) {
              state = State.READ_INDEXED_HEADER_NAME;
            } else {
              // Index was stored as the prefix
              readName(index);
              state = State.READ_LITERAL_HEADER_VALUE_LENGTH_PREFIX;
            }
          } else if ((b & 0x20) == 0x20) {
            // Dynamic Table Size Update
            if (seenOnlyDynamicTableSizeUpdate) {
              index = b & 0x1f;
              if (index == 0x1f) {
                state = State.READ_MAX_DYNAMIC_TABLE_SIZE;
              } else {
                setDynamicTableSize(index);
                state = State.READ_HEADER_REPRESENTATION;
              }
            } else {
              throw UNEXPECTED_DYNAMIC_TABLE_SIZE_UPDATE
            }
          } else {
            // Literal Header Field without Indexing / never Indexed
            seenOnlyDynamicTableSizeUpdate = false
            indexType = if ((b & 0x10) == 0x10) IndexType.NEVER else IndexType.NONE;
            index = b & 0x0f;
            if (index == 0) {
              state = State.READ_LITERAL_HEADER_NAME_LENGTH_PREFIX;
            } else if (index == 0x0f) {
              state = State.READ_INDEXED_HEADER_NAME;
            } else {
              // Index was stored as the prefix
              readName(index);
              state = State.READ_LITERAL_HEADER_VALUE_LENGTH_PREFIX;
            }
          }

        case READ_MAX_DYNAMIC_TABLE_SIZE =>
          val maxSize = decodeULE128(in);
          if (maxSize == -1) {
            return;
          }

          // Check for numerical overflow
          if (maxSize > Integer.MAX_VALUE - index) {
            throw DECOMPRESSION_EXCEPTION;
          }

          setDynamicTableSize(index + maxSize);
          state = State.READ_HEADER_REPRESENTATION;

        case READ_INDEXED_HEADER =>
          val headerIndex = decodeULE128(in);
          if (headerIndex == -1) {
            return;
          }

          // Check for numerical overflow
          if (headerIndex > Integer.MAX_VALUE - index) {
            throw DECOMPRESSION_EXCEPTION;
          }

          indexHeader(index + headerIndex, headerListener);
          state = State.READ_HEADER_REPRESENTATION;

        case READ_INDEXED_HEADER_NAME =>
          // Header Name matches an entry in the Header Table
          val nameIndex = decodeULE128(in);
          if (nameIndex == -1) {
            return;
          }

          // Check for numerical overflow
          if (nameIndex > Integer.MAX_VALUE - index) {
            throw DECOMPRESSION_EXCEPTION;
          }

          readName(index + nameIndex);
          state = State.READ_LITERAL_HEADER_VALUE_LENGTH_PREFIX;

        case READ_LITERAL_HEADER_NAME_LENGTH_PREFIX =>
          b = in.read().toByte;
          huffmanEncoded = (b & 0x80) == 0x80;
          index = b & 0x7f;
          if (index == 0x7f) {
            state = State.READ_LITERAL_HEADER_NAME_LENGTH;
          } else {
            nameLength = index;

            // Disallow empty names -- they cannot be represented in HTTP/1.x
            if (nameLength == 0) {
              throw DECOMPRESSION_EXCEPTION;
            }

            def breakable(): Unit = {
              // Check name length against max header size
              if (exceedsMaxHeaderSize(nameLength)) {

                if (indexType == IndexType.NONE) {
                  // Name is unused so skip bytes
                  name = EMPTY;
                  skipLength = nameLength;
                  state = State.SKIP_LITERAL_HEADER_NAME;
                  return;
                }

                // Check name length against max dynamic table size
                if (nameLength + HEADER_ENTRY_OVERHEAD > dynamicTable.capacity) {
                  dynamicTable.clear();
                  name = EMPTY;
                  skipLength = nameLength;
                  state = State.SKIP_LITERAL_HEADER_NAME;
                  return;
                }
              }
              state = State.READ_LITERAL_HEADER_NAME;
            }

            breakable()
          }

        case READ_LITERAL_HEADER_NAME_LENGTH =>
          // Header Name is a Literal String
          nameLength = decodeULE128(in);
          if (nameLength == -1) {
            return;
          }

          // Check for numerical overflow
          if (nameLength > Integer.MAX_VALUE - index) {
            throw DECOMPRESSION_EXCEPTION;
          }
          nameLength += index;

          def breakable(): Unit = {
            // Check name length against max header size
            if (exceedsMaxHeaderSize(nameLength)) {
              if (indexType == IndexType.NONE) {
                // Name is unused so skip bytes
                name = EMPTY;
                skipLength = nameLength;
                state = State.SKIP_LITERAL_HEADER_NAME;
                return;
              }

              // Check name length against max dynamic table size
              if (nameLength + HEADER_ENTRY_OVERHEAD > dynamicTable.capacity) {
                dynamicTable.clear();
                name = EMPTY;
                skipLength = nameLength;
                state = State.SKIP_LITERAL_HEADER_NAME;
                return;
              }
            }
            state = State.READ_LITERAL_HEADER_NAME;
          }

          breakable()

        case READ_LITERAL_HEADER_NAME =>
          // Wait until entire name is readable
          if (in.available() < nameLength) {
            return;
          }

          name = readStringLiteral(in, nameLength);

          state = State.READ_LITERAL_HEADER_VALUE_LENGTH_PREFIX;

        case SKIP_LITERAL_HEADER_NAME =>
          skipLength -= in.skip(skipLength.toLong).toInt;

          if (skipLength == 0) {
            state = State.READ_LITERAL_HEADER_VALUE_LENGTH_PREFIX;
          }

        case READ_LITERAL_HEADER_VALUE_LENGTH_PREFIX =>
          b = in.read().toByte;
          huffmanEncoded = (b & 0x80) == 0x80;
          index = b & 0x7f;
          if (index == 0x7f) {
            state = State.READ_LITERAL_HEADER_VALUE_LENGTH;
          } else {
            valueLength = index;

            // Check new header size against max header size
            val newHeaderSize = nameLength.toLong + valueLength.toLong;

            def breakable(): Unit = {
              if (exceedsMaxHeaderSize(newHeaderSize)) {
                // truncation will be reported during endHeaderBlock
                headerSize = maxHeaderSize + 1;

                if (indexType == IndexType.NONE) {
                  // Value is unused so skip bytes
                  state = State.SKIP_LITERAL_HEADER_VALUE;
                  return;
                }

                // Check new header size against max dynamic table size
                if (newHeaderSize + HEADER_ENTRY_OVERHEAD > dynamicTable.capacity) {
                  dynamicTable.clear();
                  state = State.SKIP_LITERAL_HEADER_VALUE;
                  return;
                }
              }

              if (valueLength == 0) {
                insertHeader(headerListener, name, EMPTY, indexType);
                state = State.READ_HEADER_REPRESENTATION;
              } else {
                state = State.READ_LITERAL_HEADER_VALUE;
              }
            }

            breakable()
          }

        case READ_LITERAL_HEADER_VALUE_LENGTH =>
          // Header Value is a Literal String
          valueLength = decodeULE128(in);
          if (valueLength == -1) {
            return;
          }

          // Check for numerical overflow
          if (valueLength > Integer.MAX_VALUE - index) {
            throw DECOMPRESSION_EXCEPTION;
          }
          valueLength += index;

          // Check new header size against max header size
          val newHeaderSize = nameLength.toLong + valueLength.toLong;

          def breakable(): Unit = {
            if (newHeaderSize + headerSize > maxHeaderSize) {
              // truncation will be reported during endHeaderBlock
              headerSize = maxHeaderSize + 1;

              if (indexType == IndexType.NONE) {
                // Value is unused so skip bytes
                state = State.SKIP_LITERAL_HEADER_VALUE;
                return;
              }

              // Check new header size against max dynamic table size
              if (newHeaderSize + HEADER_ENTRY_OVERHEAD > dynamicTable.capacity) {
                dynamicTable.clear();
                state = State.SKIP_LITERAL_HEADER_VALUE;
                return;
              }
            }
            state = State.READ_LITERAL_HEADER_VALUE;
          }

          breakable()

        case READ_LITERAL_HEADER_VALUE =>
          // Wait until entire value is readable
          if (in.available() < valueLength) {
            return;
          }

          val value = readStringLiteral(in, valueLength);
          insertHeader(headerListener, name, value, indexType);
          state = State.READ_HEADER_REPRESENTATION;

        case SKIP_LITERAL_HEADER_VALUE =>
          valueLength -= in.skip(valueLength.toLong).toInt;

          if (valueLength == 0) {
            state = State.READ_HEADER_REPRESENTATION;
          }

        case _ =>
          throw new IllegalStateException("should not reach here");
      }
    }
  }

  /** End the current header block. Returns if the header field has been truncated.
    * This must be called after the header block has been completely decoded.
    */
  def endHeaderBlock(): Boolean = {
    val truncated = headerSize > maxHeaderSize;
    reset();
    return truncated;
  }

  /** Set the maximum table size.
    * If this is below the maximum size of the dynamic table used by the encoder,
    * the beginning of the next header block MUST signal this change.
    */
  def setMaxHeaderTableSize(maxHeaderTableSize: Int): Unit = {
    maxDynamicTableSize = maxHeaderTableSize;
    if (maxDynamicTableSize < encoderMaxDynamicTableSize) {
      // decoder requires less space than encoder
      // encoder MUST signal this change
      maxDynamicTableSizeChangeRequired = true;
      dynamicTable.setCapacity(maxDynamicTableSize);
    }
  }

  /** Return the maximum table size.
    * This is the maximum size allowed by both the encoder and the decoder.
    */
  def getMaxHeaderTableSize(): Int =
    return dynamicTable.capacity;

  /** Return the number of header fields in the dynamic table.
    * Exposed for testing.
    */
  private[hpack] def length(): Int =
    return dynamicTable.length();

  /** Return the size of the dynamic table.
    * Exposed for testing.
    */
  private[hpack] def size(): Int =
    return dynamicTable.size;

  /** Return the header field at the given index.
    * Exposed for testing.
    */
  private[hpack] def getHeaderField(index: Int): HeaderField =
    return dynamicTable.getEntry(index + 1);

  @throws[IOException]
  private[this] def setDynamicTableSize(dynamicTableSize: Int): Unit = {
    if (dynamicTableSize > maxDynamicTableSize) {
      throw INVALID_MAX_DYNAMIC_TABLE_SIZE;
    }
    encoderMaxDynamicTableSize = dynamicTableSize;
    maxDynamicTableSizeChangeRequired = false;
    dynamicTable.setCapacity(dynamicTableSize);
  }

  @throws[IOException]
  private[this] def readName(index: Int): Unit =
    if (index <= StaticTable.length) {
      val headerField = StaticTable.getEntry(index);
      name = headerField.name;
    } else if (index - StaticTable.length <= dynamicTable.length()) {
      val headerField = dynamicTable.getEntry(index - StaticTable.length);
      name = headerField.name;
    } else {
      throw ILLEGAL_INDEX_VALUE;
    }

  @throws[IOException]
  private[this] def indexHeader(index: Int, headerListener: HeaderListener): Unit =
    if (index <= StaticTable.length) {
      val headerField = StaticTable.getEntry(index);
      addHeader(headerListener, headerField.name, headerField.value, false);
    } else if (index - StaticTable.length <= dynamicTable.length()) {
      val headerField = dynamicTable.getEntry(index - StaticTable.length);
      addHeader(headerListener, headerField.name, headerField.value, false);
    } else {
      throw ILLEGAL_INDEX_VALUE;
    }

  private[this] def insertHeader(
      headerListener: HeaderListener,
      name: Array[Byte],
      value: Array[Byte],
      indexType: IndexType,
  ): Unit = {
    addHeader(headerListener, name, value, indexType == IndexType.NEVER);

    import IndexType._

    indexType match {
      case NEVER | NONE =>

      case INCREMENTAL =>
        dynamicTable.add(new HeaderField(name, value));

      case _ =>
        throw new IllegalStateException("should not reach here");
    }
  }

  private[this] def addHeader(
      headerListener: HeaderListener,
      name: Array[Byte],
      value: Array[Byte],
      sensitive: Boolean,
  ): Unit = {
    if (name.length == 0) {
      throw new AssertionError("name is empty");
    }
    val newSize = headerSize + name.length + value.length;
    if (newSize <= maxHeaderSize) {
      headerListener.addHeader(name, value, sensitive);
      headerSize = newSize.toInt;
    } else {
      // truncation will be reported during endHeaderBlock
      headerSize = maxHeaderSize + 1;
    }
  }

  private[this] def exceedsMaxHeaderSize(size: Long): Boolean = {
    // Check new header size against max header size
    if (size + headerSize <= maxHeaderSize) {
      return false;
    }

    // truncation will be reported during endHeaderBlock
    headerSize = maxHeaderSize + 1;
    return true;
  }

  @throws[IOException]
  private[this] def readStringLiteral(in: InputStream, length: Int): Array[Byte] = {
    val buf = new Array[Byte](length);
    if (in.read(buf) != length) {
      throw DECOMPRESSION_EXCEPTION;
    }

    if (huffmanEncoded) {
      return Huffman.DECODER.decode(buf);
    } else {
      return buf;
    }
  }

  // Unsigned Little Endian Base 128 Variable-Length Integer Encoding
  @throws[IOException]
  private[this] def decodeULE128(in: InputStream): Int = {
    in.mark(5);
    var result = 0;
    var shift = 0;
    while (shift < 32) {
      if (in.available() == 0) {
        // Buffer does not contain entire integer,
        // reset reader index and return -1.
        in.reset();
        return -1;
      }
      val b = in.read().toByte;
      if (shift == 28 && (b & 0xf8) != 0) {
        break;
      }
      result |= (b & 0x7f) << shift;
      if ((b & 0x80) == 0) {
        return result;
      }
      shift += 7;
    }

    def break = {
      // Value exceeds Integer.MAX_VALUE
      in.reset();
      throw DECOMPRESSION_EXCEPTION;
    }

    break
  }
}
