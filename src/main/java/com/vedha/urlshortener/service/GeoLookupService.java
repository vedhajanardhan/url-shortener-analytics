package com.vedha.urlshortener.service;

import org.springframework.stereotype.Service;

/**
 * Stubbed IP -> country lookup. In production, swap this for a MaxMind GeoLite2
 * database (offline, no external calls) or an API like ipapi.co. Kept as an
 * interface-shaped single method so swapping the implementation is a one-file change.
 */
@Service
public class GeoLookupService {

    public String lookupCountry(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()
                || ipAddress.startsWith("127.") || ipAddress.equals("0:0:0:0:0:0:0:1")) {
            return "Unknown";
        }
        // TODO: replace with real GeoIP2 lookup, e.g.:
        // CountryResponse r = geoIpDatabaseReader.country(InetAddress.getByName(ipAddress));
        // return r.getCountry().getName();
        return "Unknown";
    }
}
