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
package com.twitter.hpack;

import com.twitter.hpack.HeaderField.HEADER_ENTRY_OVERHEAD;

final class DynamicTable {

  // a circular queue of header fields
  var headerFields: Array[HeaderField] = _;
  var head: Int = _;
  var tail: Int = _;
  var size: Int = _;
  var capacity = -1; // ensure setCapacity creates the array

  /** Creates a new dynamic table with the specified initial capacity.
    */
  def this(initialCapacity: Int) {
    this()
    setCapacity(initialCapacity);
  }

  /** Return the number of header fields in the dynamic table.
    */
  def length(): Int = {
    var length: Int = 0;
    if (head < tail) {
      length = headerFields.length - tail + head;
    } else {
      length = head - tail;
    }
    return length;
  }

  /** Return the header field at the given index.
    * The first and newest entry is always at index 1,
    * and the oldest entry is at the index length().
    */
  def getEntry(index: Int): HeaderField = {
    if (index <= 0 || index > length()) {
      throw new IndexOutOfBoundsException();
    }
    val i = head - index;
    if (i < 0) {
      return headerFields(i + headerFields.length);
    } else {
      return headerFields(i);
    }
  }

  /** Add the header field to the dynamic table.
    * Entries are evicted from the dynamic table until the size of the table
    * and the new header field is less than or equal to the table's capacity.
    * If the size of the new entry is larger than the table's capacity,
    * the dynamic table will be cleared.
    */
  def add(header: HeaderField): Unit = {
    val headerSize = header.size();
    if (headerSize > capacity) {
      clear();
      return;
    }
    while (size + headerSize > capacity)
      remove();
    headerFields(head) = header;
    head += 1
    size += header.size();
    if (head == headerFields.length) {
      head = 0;
    }
  }

  /** Remove and return the oldest header field from the dynamic table.
    */
  def remove(): HeaderField = {
    val removed = headerFields(tail);
    if (removed == null) {
      return null;
    }
    size -= removed.size();
    headerFields(tail) = null;
    tail += 1
    if (tail == headerFields.length) {
      tail = 0;
    }
    return removed;
  }

  /** Remove all entries from the dynamic table.
    */
  def clear(): Unit = {
    while (tail != head) {
      headerFields(tail) = null;
      tail += 1
      if (tail == headerFields.length) {
        tail = 0;
      }
    }
    head = 0;
    tail = 0;
    size = 0;
  }

  /** Set the maximum size of the dynamic table.
    * Entries are evicted from the dynamic table until the size of the table
    * is less than or equal to the maximum size.
    */
  def setCapacity(capacity: Int): Unit = {
    if (capacity < 0) {
      throw new IllegalArgumentException("Illegal Capacity: " + capacity);
    }

    // initially capacity will be -1 so init won't return here
    if (this.capacity == capacity) {
      return;
    }
    this.capacity = capacity;

    if (capacity == 0) {
      clear();
    } else {
      // initially size will be 0 so remove won't be called
      while (size > capacity)
        remove();
    }

    var maxEntries = capacity / HEADER_ENTRY_OVERHEAD;
    if (capacity % HEADER_ENTRY_OVERHEAD != 0) {
      maxEntries += 1;
    }

    // check if capacity change requires us to reallocate the array
    if (headerFields != null && headerFields.length == maxEntries) {
      return;
    }

    var tmp = new Array[HeaderField](maxEntries);

    // initially length will be 0 so there will be no copy
    val len = length();
    var cursor = tail;
    var i = 0;
    while (i < len) {
      val entry = headerFields(cursor);
      cursor += 1
      tmp(i) = entry;
      if (cursor == headerFields.length) {
        cursor = 0;
      }
      i += 1
    }

    this.tail = 0;
    this.head = tail + len;
    this.headerFields = tmp;
  }
}
