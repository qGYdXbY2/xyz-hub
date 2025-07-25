/*
 * Copyright (C) 2017-2025 HERE Europe B.V.
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

package com.here.xyz.jobs.steps.execution;

import com.here.xyz.jobs.steps.Step;
import java.util.List;

public class JobInternalDelegateStep extends DelegateStep {

  //Only needed for deserialization purposes
  private JobInternalDelegateStep() {
    super();
  }

  public JobInternalDelegateStep(Step delegate, List<OutputSet> outputSets) {
    super(delegate, delegate, outputSets);
  }

  @Override
  protected void setJobId(String jobId) {
    if(getDelegator() != null)
      getDelegator().withJobId(jobId);
  }

  private void setDelegate(Step delegate) {
    this.delegate = delegate;
    this.delegator = delegate;
  }

}
