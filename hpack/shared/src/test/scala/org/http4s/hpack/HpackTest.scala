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

import org.junit.Test

object HpackTest {
  val TEST_DIR = "/hpack/"
}

class HpackTest {
  def test(implicit name: sourcecode.Name): Unit = {
    val testCase = TestCase.load(HpackTest.TEST_DIR + name.value + ".json")
    testCase.testCompress()
    testCase.testDecompress()
  }

  @Test def testDuplicateHeaders = test
  @Test def testEmpty = test
  @Test def testEviction = test
  @Test def testMaxHeaderTableSize = test
  @Test def testSpecExampleC2_1 = test
  @Test def testSpecExampleC2_2 = test
  @Test def testSpecExampleC2_3 = test
  @Test def testSpecExampleC2_4 = test
  @Test def testSpecExampleC3 = test
  @Test def testSpecExampleC4 = test
  @Test def testSpecExampleC5 = test
  @Test def testSpecExampleC6 = test
  @Test def testStaticTableEntries = test
}
