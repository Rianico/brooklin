package com.linkedin.datastream.server;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.linkedin.datastream.common.Datastream;
import com.linkedin.datastream.common.DatastreamUtils;
import com.linkedin.datastream.server.api.connector.DatastreamDeduper;
import com.linkedin.datastream.server.api.connector.DatastreamValidationException;

import static com.linkedin.datastream.common.DatastreamUtils.getEnvelopeSerDe;
import static com.linkedin.datastream.common.DatastreamUtils.getKeySerDe;
import static com.linkedin.datastream.common.DatastreamUtils.getPayloadSerDe;


/**
 * Abstract class for implementing dedupers conforming some basic requirements:
 *
 * 1) reuse is allowed per datastream metadata for both new and existing streams
 * 2) existing streams have valid source/destination
 * 3) existing and new streams have the same transport provider
 * 4) existing and new streams have the same serdes
 *
 * Deduper subclasses should override {@link AbstractDatastreamDeduper#dedupStreams}
 * to apply any additional dedup logic.
 */
public abstract class AbstractDatastreamDeduper implements DatastreamDeduper {
  @Override
  public Optional<Datastream> findExistingDatastream(Datastream stream, List<Datastream> allStreams)
      throws DatastreamValidationException {
    if (allStreams == null || allStreams.isEmpty()) {
      return Optional.empty();
    }

    String transportName = stream.getTransportProviderName();

    // Only keep streams meeting basic reuse requirements
    List<Datastream> reuseCandidates = allStreams
        .stream()
        .filter(DatastreamUtils::isReuseAllowed)
        .filter(DatastreamUtils::hasValidSource)
        .filter(DatastreamUtils::hasValidDestination)
        .filter(d -> d.getTransportProviderName().equals(transportName))
        .filter(d -> equalSerdes(stream, d))
        .collect(Collectors.toList());

    if (!reuseCandidates.isEmpty()) {
      return dedupStreams(stream, reuseCandidates);
    } else {
      return Optional.empty();
    }
  }

  protected static boolean equalSerdes(Datastream newStream, Datastream curStream) {
    return getKeySerDe(newStream).equals(getKeySerDe(curStream))
        && getPayloadSerDe(newStream).equals(getPayloadSerDe(curStream))
        && getEnvelopeSerDe(newStream).equals(getEnvelopeSerDe(curStream));
  }

  /**
   * Deduper implementations should override this to apply additional dedup logic.
   * @param stream new streams to be de-duped
   * @param candidates existings streams meeting basic requirements for reuse
   * @return optional wrapper of the reusable stream
   */
  protected abstract Optional<Datastream> dedupStreams(Datastream stream, List<Datastream> candidates)
      throws DatastreamValidationException;
}
