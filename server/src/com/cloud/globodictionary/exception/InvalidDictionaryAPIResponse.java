package com.cloud.globodictionary.exception;

import java.io.IOException;

public class InvalidDictionaryAPIResponse extends Exception{

    public InvalidDictionaryAPIResponse() {
        super();
    }

    public InvalidDictionaryAPIResponse(IOException e) {
        super(e);
    }
}
