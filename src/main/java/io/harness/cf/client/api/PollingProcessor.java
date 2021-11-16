package io.harness.cf.client.api;

import com.google.common.util.concurrent.AbstractScheduledService;
import io.harness.cf.ApiException;
import io.harness.cf.api.ClientApi;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.plexus.util.StringUtils;

@Slf4j
class PollingProcessor extends AbstractScheduledService {

  @Setter private String environment;
  @Setter private String cluster;

  private final ClientApi api;
  private final int pollIntervalSeconds;
  private final Repository repository;
  private boolean initialized = false;
  private final PollerCallback callback;

  public PollingProcessor(
      ClientApi api, Repository repository, int pollIntervalSeconds, PollerCallback callback) {
    this.api = api;
    this.pollIntervalSeconds = pollIntervalSeconds;
    this.repository = repository;
    this.callback = callback;
  }

  public CompletableFuture<List<FeatureConfig>> retrieveFlags() {
    CompletableFuture<List<FeatureConfig>> completableFuture = new CompletableFuture<>();
    try {
      log.debug("Fetching flags started");
      List<FeatureConfig> featureConfig = this.api.getFeatureConfig(this.environment, this.cluster);
      log.debug("Fetching flags finished");
      featureConfig.forEach(fc -> repository.setFlag(fc.getFeature(), fc));
      completableFuture.complete(featureConfig);
    } catch (ApiException e) {
      log.error("Error loading flags, err: {}", e.getMessage());
      completableFuture.completeExceptionally(e);
    }
    return completableFuture;
  }

  public CompletableFuture<List<Segment>> retrieveSegments() {
    CompletableFuture<List<Segment>> completableFuture = new CompletableFuture<>();
    try {
      log.debug("Fetching segments started");
      List<Segment> segments = this.api.getAllSegments(this.environment, this.cluster);
      log.debug("Fetching segments finished");
      segments.forEach(s -> repository.setSegment(s.getIdentifier(), s));
      completableFuture.complete(segments);
    } catch (ApiException e) {
      log.error("Error loading segments, err: {}", e.getMessage());
      completableFuture.completeExceptionally(e);
    }
    return completableFuture;
  }

  @Override
  protected void runOneIteration() {
    if (StringUtils.isBlank(environment) && StringUtils.isBlank(cluster)) {
      log.warn("Environment or cluster is missing");
      return;
    }
    log.debug("running poll iteration");
    try {
      CompletableFuture.allOf(retrieveFlags(), retrieveSegments()).join();
      if (!initialized) {
        initialized = true;
        log.info("PollingProcessor initialized");
        callback.onPollerReady();
      }
    } catch (CompletionException exc) {
      log.error("Error polling the data, err: {}", exc.getMessage());
      callback.onPollerError(exc.getMessage());
    }
  }

  @NonNull
  @Override
  protected Scheduler scheduler() {
    // first argument is for initial delay so this should be always 0
    return Scheduler.newFixedDelaySchedule(0, pollIntervalSeconds, TimeUnit.SECONDS);
  }

  public void start() {
    log.info("Starting PollingProcessor with request interval: {}", pollIntervalSeconds);
    startAsync();
  }

  public void stop() {
    log.info("Stopping PollingProcessor");
    stopAsync();
  }

  public void close() {
    stop();
    log.info("Closing PollingProcessor");
  }
}