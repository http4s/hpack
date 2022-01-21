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

import java.io.File
import java.util
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters


@RunWith(classOf[Parameterized]) object HpackTest {
  private val TEST_DIR = "/hpack/"

  @Parameters(name = "{0}") def data: util.Collection[Array[AnyRef]] = {
    val url = classOf[HpackTest].getResource(TEST_DIR)
    val files = new File(url.getFile).listFiles
    if (files == null) throw new NullPointerException("files")
    val data = new util.ArrayList[Array[AnyRef]]
    for (file <- files) {
      data.add(Array[AnyRef](file.getName))
    }
    data
  }
}

@RunWith(classOf[Parameterized]) class HpackTest(val fileName: String) {
  @Test
  @throws[Exception]
  def test(): Unit = {
    val is = classOf[HpackTest].getResourceAsStream(HpackTest.TEST_DIR + fileName)
    val testCase = TestCase.load(is)
    testCase.testCompress()
    testCase.testDecompress()
  }
}
