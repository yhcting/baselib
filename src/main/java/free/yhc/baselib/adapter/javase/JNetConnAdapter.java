package free.yhc.baselib.adapter.javase;

import free.yhc.baselib.adapter.NetConnAdapter;
import free.yhc.baselib.net.NetConn;

public class JNetConnAdapter implements NetConnAdapter {
    @Override
    public int
    getConnectedNetworks() {
        // TODO: Actually, it's NOT correctly implemented yet!
        return NetConn.TYPE_ETHERNET;
    }
}
