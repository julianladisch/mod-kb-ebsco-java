package org.folio.rest.converter;

import java.util.List;
import java.util.stream.Collectors;

import org.folio.rest.jaxrs.model.ProxyTypes;
import org.folio.rest.jaxrs.model.ProxyTypesData;
import org.folio.rest.jaxrs.model.ProxyTypesDataAttributes;
import org.folio.rest.util.RestConstants;
import org.folio.rmapi.model.Proxies;
import org.folio.rmapi.model.ProxyWithUrl;

public class ProxyConverter {

  public ProxyTypes convert(Proxies proxies) {
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