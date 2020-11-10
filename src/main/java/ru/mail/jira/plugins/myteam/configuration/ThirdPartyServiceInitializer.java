/* (C)2020 */
package ru.mail.jira.plugins.myteam.configuration;

import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import com.mashape.unirest.http.ObjectMapper;
import com.mashape.unirest.http.Unirest;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ExportAsService(LifecycleAware.class)
public class ThirdPartyServiceInitializer implements LifecycleAware {

  @Override
  public void onStart() {
    log.info("Init Unirest");
    Unirest.setTimeouts(10_000, 300_000);
    Unirest.setObjectMapper(
        new ObjectMapper() {
          private org.codehaus.jackson.map.ObjectMapper jacksonObjectMapper =
              new org.codehaus.jackson.map.ObjectMapper();

          @Override
          public <T> T readValue(String value, Class<T> valueType) {
            try {
              return jacksonObjectMapper.readValue(value, valueType);
            } catch (IOException e) {
              log.error(
                  String.format(
                      "Unirest JacksonObjectMapper exception during reading value = %s as type = %s",
                      value, valueType.toString()),
                  e);
              throw new RuntimeException(e);
            }
          }

          @Override
          public String writeValue(Object value) {
            try {
              return jacksonObjectMapper.writeValueAsString(value);
            } catch (IOException e) {
              log.error(
                  String.format(
                      "Unirest JacksonObjectMapper exception during writing  value = %s ", value),
                  e);
              throw new RuntimeException(e);
            }
          }
        });
  }

  @Override
  public void onStop() {
    try {
      log.info("Stop Unirest");
      Unirest.shutdown();
    } catch (IOException e) {
    }
  }
}
