package platform.exceptionloggin;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExceptionLoggingPropertiesTest {
    @Test
    void rejectsInvalidHttpHeaderNames() {
        ExceptionLoggingProperties properties = new ExceptionLoggingProperties();

        assertThatThrownBy(() -> properties.setTraceHeaderName("X-Trace-Id\r\nInjected"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setCorrelationHeaderName(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void copiesAndCleansConfiguredSets() {
        ExceptionLoggingProperties properties = new ExceptionLoggingProperties();
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        hosts.add(" service.test ");
        hosts.add(" ");
        hosts.add(null);

        properties.setTracePropagationAllowedHosts(hosts);
        hosts.add("later.test");

        assertThat(properties.getTracePropagationAllowedHosts()).containsExactly("service.test");
        assertThatThrownBy(() -> properties.getTracePropagationAllowedHosts().add("mutate.test"))
                .isInstanceOf(UnsupportedOperationException.class);
        properties.setAdditionalSensitiveFields(Set.of(" internalReference "));
        assertThat(properties.getAdditionalSensitiveFields()).containsExactly("internalReference");
    }
}
