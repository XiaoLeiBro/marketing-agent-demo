package cc.utime.marketingagent.trace;

import cc.utime.marketingagent.domain.AgentTrace;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryTraceRepository implements TraceRepository {

  private final ConcurrentMap<String, AgentTrace> traces = new ConcurrentHashMap<>();

  @Override
  public void save(AgentTrace trace) {
    this.traces.put(trace.traceId(), trace);
  }

  @Override
  public Optional<AgentTrace> findById(String traceId) {
    return Optional.ofNullable(this.traces.get(traceId));
  }
}
