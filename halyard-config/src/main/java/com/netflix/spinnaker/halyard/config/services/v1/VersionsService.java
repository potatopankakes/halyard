/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.config.v1.RelaxedObjectMapper;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.memoize.v1.ExpiringConcurrentMap;
import com.netflix.spinnaker.halyard.core.registry.v1.BillOfMaterials;
import com.netflix.spinnaker.halyard.core.registry.v1.ProfileRegistry;
import com.netflix.spinnaker.halyard.core.registry.v1.Versions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import retrofit.RetrofitError;

import java.io.IOException;
import java.util.Optional;

import static com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity.FATAL;

@Component
public class VersionsService {
  @Autowired
  ProfileRegistry profileRegistry;

  @Autowired
  Yaml yamlParser;

  @Autowired
  RelaxedObjectMapper relaxedObjectMapper;

  static private ExpiringConcurrentMap<String, String> concurrentMap = ExpiringConcurrentMap.fromMinutes(10);

  static private String latestHalyardKey = "__latest-halyard__";

  static private String latestSpinnakerKey = "__latest-spinnaker__";

  public Versions getVersions() {
    try {
      return relaxedObjectMapper.convertValue(
          yamlParser.load(profileRegistry.getObjectContents("versions.yml")),
          Versions.class
      );
    } catch (IOException e) {
      throw new HalException(
          new ConfigProblemBuilder(FATAL, "Could not load \"versions.yml\" from config bucket: " + e.getMessage() + ".").build());
    }
  }

  public BillOfMaterials getBillOfMaterials(String version) {
    if (version == null || version.isEmpty()) {
      throw new HalException(
          new ConfigProblemBuilder(FATAL,
              "You must pick a version of Spinnaker to deploy.")
              .build()
      );
    }

    try {
      String bomName = ProfileRegistry.bomPath(version);

      BillOfMaterials bom = relaxedObjectMapper.convertValue(
          yamlParser.load(profileRegistry.getObjectContents(bomName)),
          BillOfMaterials.class
      );

      return bom;
    } catch (RetrofitError | IOException e) {
      throw new HalException(
          new ConfigProblemBuilder(FATAL,
              "Unable to retrieve the Spinnaker bill of materials for version \"" + version + "\": " + e.getMessage())
              .build()
      );
    }
  }

  public String getLatestHalyardVersion() {
    String result = concurrentMap.get(latestHalyardKey);
    if (result == null) {
      result = getVersions().getLatestHalyard();
      concurrentMap.put(latestHalyardKey, result);
    }

    return result;
  }

  public String getRunningHalyardVersion() {
    return Optional.ofNullable(VersionsService.class
        .getPackage()
        .getImplementationVersion()).orElse("Unknown");
  }

  public String getLatestSpinnakerVersion() {
    String result = concurrentMap.get(latestSpinnakerKey);
    if (result == null) {
      result = getVersions().getLatestSpinnaker();
      concurrentMap.put(latestSpinnakerKey, result);
    }

    return result;
  }
}
