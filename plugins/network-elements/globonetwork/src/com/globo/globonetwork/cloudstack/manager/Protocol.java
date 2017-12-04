package com.globo.globonetwork.cloudstack.manager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Protocol {


    public enum L4 {
        TCP("TCP", Arrays.asList(L7.values())),
        UDP("UDP", Collections.singletonList(L7.OTHERS));

        private String networkApiOptionValue;
        private List<L7> l7s;

        L4(String networkApiOptionValue, List<L7> l7values) {
            this.networkApiOptionValue = networkApiOptionValue;
            this.l7s = l7values;
        }

        public static String getNetworkAPIValues() {

            List<String> values = new ArrayList<>();
            for (L7 protocol : Protocol.L7.values()) {
                values.add(protocol.networkApiOptionValue);
            }
            return values.toString();
        }


        public String getNetworkApiOptionValue() {
            return networkApiOptionValue;
        }

        public List<L7> getL7s() {
            return l7s;
        }

        public static L4 valueOfFromNetworkAPI(String value) {
            for (L4 protocol : Protocol.L4.values()) {

                if (protocol.networkApiOptionValue.equals(value)) {
                    return protocol;
                }
            }
            return null;
        }
    }


    public enum L7{
        HTTP("HTTP"),
        HTTPS("HTTPS"),
        OTHERS("Outros");

        private String networkApiOptionValue;
        L7(String networkApiOptionValue) {
            this.networkApiOptionValue = networkApiOptionValue;
        }

        public static String getNetworkAPIValues() {

            List<String> values = new ArrayList<>();
            for (L7 protocol : Protocol.L7.values()) {
                values.add(protocol.networkApiOptionValue);
            }
            return values.toString();
        }

        public String getNetworkApiOptionValue() {
            return networkApiOptionValue;
        }

        public static L7 valueOfFromNetworkAPI(String value) {
            for (L7 protocol : Protocol.L7.values()) {

                if (protocol.networkApiOptionValue.equals(value)) {
                    return protocol;
                }
            }
            return null;
        }

    }

    public static boolean validValueL7(String value) {

        for (L7 l7 : L7.values()) {
            if (l7.networkApiOptionValue.equals(value)) {
                return true;
            }
        }
        return false;

    }

    public static boolean validValueL4(String value) {

        for (L4 l4 : L4.values()) {
            if (l4.name().equals(value)) {
                return true;
            }
        }
        return false;

    }

    public static boolean validProtocols(L4 l4, L7 l7) {

        if (l4.getL7s().contains(l7)) {
            return true;
        }

        return false;
    }
}

