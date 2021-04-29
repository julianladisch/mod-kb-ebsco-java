package org.folio.service.uc;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.folio.rest.jaxrs.model.UCCredentialsPresence;

public interface UCAuthService {

  CompletableFuture<String> authenticate(Map<String, String> okapiHeaders);

  CompletionStage<UCCredentialsPresence> checkCredentialsPresence(Map<String, String> okapiHeaders);
}
