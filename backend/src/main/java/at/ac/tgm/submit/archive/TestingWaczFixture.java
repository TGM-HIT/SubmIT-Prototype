package at.ac.tgm.submit.archive;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds the small, harmless WACZ used by the opt-in testing preview. */
final class TestingWaczFixture {
    private TestingWaczFixture() {
    }

    static byte[] create() {
        try {
            List<WarcResponse> responses = List.of(
                    new WarcResponse("https://snapshot.example.test/index.html", "text/html; charset=utf-8",
                            """
                            <!doctype html><html><head><title>Fixture home</title></head><body>
                            <h1>Reviewed archive home</h1>
                            <script>
                            window.__archiveProbeExecuted = true;
                            document.cookie = 'archiveProbe=1';
                            localStorage.setItem('archiveProbe', '1');
                            </script>
                            <img alt="Bundled archive asset" src="assets/pixel.svg">
                            <a href="pages/about.html">Open captured relative page</a>
                            </body></html>
                            """.getBytes(StandardCharsets.UTF_8)),
                    new WarcResponse("https://snapshot.example.test/pages/about.html", "text/html; charset=utf-8",
                            """
                            <!doctype html><html><head><title>Fixture detail</title></head><body>
                            <h1>Captured relative page</h1>
                            <img alt="Bundled archive asset" src="../assets/pixel.svg">
                            <a href="../index.html">Return to captured home</a>
                            </body></html>
                            """.getBytes(StandardCharsets.UTF_8)),
                    new WarcResponse("https://snapshot.example.test/assets/pixel.svg", "image/svg+xml",
                            """
                            <svg xmlns="http://www.w3.org/2000/svg" width="48" height="32" viewBox="0 0 48 32">
                              <rect width="48" height="32" rx="4" fill="#2563eb"/>
                              <circle cx="24" cy="16" r="8" fill="#ffffff"/>
                            </svg>
                            """.getBytes(StandardCharsets.UTF_8)));
            byte[] compressedWarc = gzip(warc(responses));
            StringBuilder cdxj = cdxj(responses, compressedWarc.length);
            byte[] manifest = """
                    {"profile":"data-package","wacz_version":"1.1.1","resources":[
                    {"name":"archive","path":"archive/snapshot.warc.gz","mediatype":"application/gzip"},
                    {"name":"index","path":"indexes/index.cdxj","mediatype":"application/x-cdxj"}
                    ]}
                    """.getBytes(StandardCharsets.UTF_8);
            byte[] pages = """
                    {"url":"https://snapshot.example.test/index.html","timestamp":"20260101000000","title":"Fixture home"}
                    {"url":"https://snapshot.example.test/pages/about.html","timestamp":"20260101000000","title":"Fixture detail"}
                    """.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                addZip(zip, "datapackage.json", manifest);
                addZip(zip, "indexes/index.cdxj", cdxj.toString().getBytes(StandardCharsets.UTF_8));
                addZip(zip, "pages/pages.jsonl", pages);
                addZip(zip, "archive/snapshot.warc.gz", compressedWarc);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create the testing WACZ archive fixture.", exception);
        }
    }

    private static StringBuilder cdxj(List<WarcResponse> responses, int compressedLength) {
        StringBuilder result = new StringBuilder();
        for (WarcResponse response : responses) {
            result.append(response.url()).append(" 20260101000000 {\"url\":\"").append(response.url())
                    .append("\",\"filename\":\"archive/snapshot.warc.gz\",\"offset\":0,\"length\":")
                    .append(compressedLength).append(",\"mime\":\"")
                    .append(response.mimeType()).append("\",\"status\":\"200\"}\n");
        }
        return result;
    }

    private static byte[] warc(List<WarcResponse> responses) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (WarcResponse response : responses) {
            byte[] http = ("HTTP/1.1 200 OK\r\nContent-Type: " + response.mimeType()
                    + "\r\nContent-Length: " + response.body().length + "\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1);
            ByteArrayOutputStream block = new ByteArrayOutputStream();
            block.write(http);
            block.write(response.body());
            byte[] record = block.toByteArray();
            output.write(("WARC/1.1\r\nWARC-Type: response\r\nWARC-Target-URI: " + response.url()
                    + "\r\nContent-Type: application/http; msgtype=response\r\nContent-Length: "
                    + record.length + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
            output.write(record);
            output.write("\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
        }
        return output.toByteArray();
    }

    private static byte[] gzip(byte[] bytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(bytes);
        }
        return output.toByteArray();
    }

    private static void addZip(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private record WarcResponse(String url, String mimeType, byte[] body) {
    }
}
