package io.harness.cf.client.api;

import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

interface Query {

  Optional<FeatureConfig> getFlag(@NonNull String identifier);

  Optional<Segment> getSegment(@NonNull String identifier);

  List<String> findFlagsBySegment(@NonNull String identifier);
}
