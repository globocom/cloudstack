package com.globo.globonetwork.cloudstack.commands;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.globo.globonetwork.client.api.GloboNetworkAPI;
import com.globo.globonetwork.client.exception.GloboNetworkErrorCodeException;
import com.globo.globonetwork.client.exception.GloboNetworkException;
import java.nio.charset.Charset;
import org.apache.log4j.Logger;

public abstract class GloboNetworkResourceCommand  extends Command {

    private static final Logger s_logger = Logger.getLogger(GloboNetworkResourceCommand.class);

    @Override
    public boolean executeInSequence() {
        return false;
    }


    public abstract Answer execute(GloboNetworkAPI api);


    public static Answer handleGloboNetworkException(Command cmd, GloboNetworkException e) {
        if (e instanceof GloboNetworkErrorCodeException) {
            GloboNetworkErrorCodeException ex = (GloboNetworkErrorCodeException)e;
            s_logger.error("Error accessing GloboNetwork: " + ex.getCode() + " - " + ex.getDescription(), ex);
            return new GloboNetworkErrorAnswer(cmd, ex.getCode(), getUtf8(ex.getDescription()));
        } else {
            s_logger.error("Generic error accessing GloboNetwork", e);
            return new Answer(cmd, false, e.getMessage());
        }
    }
    public static String getUtf8(String value) {
        try {
            Charset charsetISO = Charset.forName("ISO_8859_1");
            byte text[] = value.getBytes(charsetISO);
            return new String(text, Charset.defaultCharset());
        } catch (Exception e) {
            s_logger.warn("could not convert message: " + value + " error:" + e.getMessage());
        }
        return value;
    }
}
