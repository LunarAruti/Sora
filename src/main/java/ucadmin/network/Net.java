package ucadmin.network;

import ucadmin.exceptions.NetworkException;
import ucadmin.util.Logger;
import ucadmin.util.Logger.TAG;

/**
 * Public entry point for executing network requests.
 */
public final class Net {
    private Net() {}

    public static boolean request(NetworkRequest req) throws NetworkException {
        if (req == null)
            throw new IllegalArgumentException("request: req is null");
        if (!req.isSealed())
            throw new IllegalStateException("REQUEST_NOT_SEALED: Call seal() before executing.");

        Logger.log(TAG.REQUEST,
                "Network request started → " + req.getService() + ":" + req.getName() +
                        " " + req.getType() + " trace=" + req.getTraceId());

        boolean result = NetworkClient.execute(req);

        Logger.log(TAG.REQUEST,
                "Network request complete ← " + req.getService() + ":" + req.getName() +
                        " trace=" + req.getTraceId() + " stored=" + result +
                        " path=" + req.getCachePath());

        return result;
    }
}
