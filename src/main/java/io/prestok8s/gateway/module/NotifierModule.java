package io.prestok8s.gateway.module;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.prestok8s.baseapp.AppModule;
import io.prestok8s.gateway.config.GatewayConfiguration;
import io.prestok8s.gateway.config.NotifierConfiguration;
import io.prestok8s.gateway.notifier.EmailNotifier;
import io.prestok8s.gateway.notifier.Notifier;
import io.dropwizard.setup.Environment;

public class NotifierModule extends AppModule<GatewayConfiguration, Environment> {

  public NotifierModule(GatewayConfiguration config, Environment env) {
    super(config, env);
  }

  @Provides
  @Singleton
  public Notifier provideNotifier() {
    NotifierConfiguration notifierConfiguration = getConfiguration().getNotifier();
    return new EmailNotifier(notifierConfiguration);
  }
}
