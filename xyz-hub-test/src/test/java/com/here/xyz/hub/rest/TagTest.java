/*
 * Copyright (C) 2017-2024 HERE Europe B.V.
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
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package com.here.xyz.hub.rest;

import static com.here.xyz.models.hub.Tag.isValidId;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TagTest {

  @Test
  public void testInvalidTagNames() {
    assertFalse(isValidId(""));
    assertFalse(isValidId("  "));
    assertFalse(isValidId("  abc"));
    assertFalse(isValidId("  abc"));
    assertFalse(isValidId("1abc"));
    assertFalse(isValidId("abc "));
    assertFalse(isValidId("abcdefghijabcdefghijabcdefghijabcdefghijabcdefghijX"));
    assertFalse(isValidId("HEAD"));
    assertFalse(isValidId("*"));
  }

  @Test
  public void testValidTagNames() {
    assertTrue(isValidId("abcdefghij"));
    assertTrue(isValidId("head"));
    assertTrue(isValidId("a1bc"));
    assertTrue(isValidId("abc"));
    assertTrue(isValidId("a"));
  }
}
