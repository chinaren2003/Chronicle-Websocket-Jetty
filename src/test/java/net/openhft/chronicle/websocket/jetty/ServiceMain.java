package net.openhft.chronicle.websocket.jetty;

import net.openhft.chronicle.core.jlbh.JLBH;
import net.openhft.chronicle.core.jlbh.JLBHOptions;
import net.openhft.chronicle.core.jlbh.JLBHTask;
import net.openhft.chronicle.core.util.NanoSampler;
import net.openhft.chronicle.wire.MarshallableOut;
import net.openhft.chronicle.wire.VanillaWireParser;
import net.openhft.chronicle.wire.WireParser;

import java.io.IOException;

/**
 * Created by peter on 23/04/16.
 */
public class ServiceMain {
    // -XX:+UnlockCommercialFeatures    -XX:+FlightRecorder    -XX:StartFlightRecording=dumponexit=true,filename=ServiceMain.jfr,settings=profile2    -XX:+UnlockDiagnosticVMOptions    -XX:+DebugNonSafepoints
    public static void main(String[] args) throws IOException {
        JLBH jlbh = new JLBH(new JLBHOptions().runs(6)
                .warmUpIterations(50000)
                .iterations(50000)
                .throughput(20000)
                .accountForCoordinatedOmmission(false)
                .recordOSJitter(false)
                .jlbhTask(new JLBHTask() {
                    JettyWebSocketClient client1;

                    @Override
                    public void init(JLBH jlbh) {
                        WireParser<MarshallableOut> parser = new VanillaWireParser<>((s, v, o) ->
                                jlbh.sampleNanos(System.nanoTime() - v.int64()));
                        try {
                            client1 = new JettyWebSocketClient("ws://localhost:9090/echo/", parser);
                        } catch (IOException e) {
                            throw new AssertionError(e);
                        }
                    }

                    @Override
                    public void run(long startTimeNS) {
                        client1.writeDocument(w -> w.writeEventName("echo").int64(System.nanoTime()));
                    }
                }));

        JettyWebSocketServer server = new JettyWebSocketServer(9090);
        NanoSampler probe = jlbh.addProbe("on server");
        server.addService("/echo/*", Echo.class, r -> new EchoImpl(r) {
            @Override
            public void echo(long time) {
                super.echo(System.nanoTime());
                probe.sampleNanos(System.nanoTime() - time);
            }
        });
        server.start();

        jlbh.start();

        server.close();
        System.exit(0);
    }
}
