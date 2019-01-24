package org.folio.rest.converter.proxy;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import org.folio.rest.jaxrs.model.ProxyTypes;
import org.folio.rest.jaxrs.model.ProxyTypesData;
import org.folio.rest.jaxrs.model.ProxyTypesDataAttributes;
import org.folio.rest.util.RestConstants;
import org.folio.rmapi.model.Proxies;
import org.folio.rmapi.model.ProxyWithUrl;

@Component
public class ProxiesConverter implements Converter<Proxies, ProxyTypes> {

  @Override
  public ProxyTypes convert(@NonNull Proxies proxies) {
    List<ProxyTypesData> providerList = proxies.getProxyList().stream()
      .map(this::convertProxy)
      .collect(Collectors.toList());

    return new ProxyTypes().withJsonapi(RestConstants.JSONAPI).withData(providerList);
  }

  private ProxyTypesData convertProxy(ProxyWithUrl proxy) {
    return new ProxyTypesData()
      .withId(proxy.getId())
      .withType(ProxyTypesData.Type.PROXY_TYPES)
      .withAttributes(new ProxyTypesDataAttributes()
        .withId(proxy.getId())
        .withName(proxy.getName())
        .withUrlMask(proxy.getUrlMask())
      );
  }

}