package cc.utime.marketingagent.trace;

import cc.utime.marketingagent.domain.AgentTrace;
import java.util.Optional;

public interface TraceRepository {

  void save(AgentTrace trace);

  Optional<AgentTrace> findById(String traceId);
}
