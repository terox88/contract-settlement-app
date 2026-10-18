package pl.company.settlements.file.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

public final class AttachmentArchiveCases {
    public static void run() throws Exception {
        eq("Oświadczenie firmy A", AttachmentNames.validate("  Oświadczenie firmy A  "));
        reject(() -> AttachmentNames.validate(" ")); reject(() -> AttachmentNames.validate("x".repeat(256))); reject(() -> AttachmentNames.validate("a\nb"));
        eq("Oświadczenie firmy A.pdf", AttachmentNames.downloadName("Oświadczenie firmy A","scan.pdf"));
        eq("Faktura.XML", AttachmentNames.downloadName("Faktura.XML","scan.xml"));
        if (AttachmentNames.downloadName("../../plik", "scan.pdf").contains("/")) throw new AssertionError("Path traversal");
        List<AttachmentZipWriter.Entry> list = new ArrayList<>();
        list.add(new AttachmentZipWriter.Entry(1,"CONTRACT","../Umowa","scan.pdf",()->new ByteArrayInputStream("PDF1".getBytes(StandardCharsets.UTF_8))));
        list.add(new AttachmentZipWriter.Entry(2,"CONTRACT","../Umowa","scan.pdf",()->new ByteArrayInputStream("PDF2".getBytes(StandardCharsets.UTF_8))));
        list.add(new AttachmentZipWriter.Entry(2,"CONTRACT","duplikat","scan.pdf",()->{throw new AssertionError("Duplicate attachment must not be opened");}));
        list.add(new AttachmentZipWriter.Entry(3,"SUBCONTRACTOR_STATEMENT","Oświadczenie","scan.pdf",()->new ByteArrayInputStream("PDF3".getBytes(StandardCharsets.UTF_8))));
        class Output extends ByteArrayOutputStream { boolean closed; public void close(){closed=true;} }
        Output output = new Output(); AttachmentZipWriter.write(list,output); if(output.closed)throw new AssertionError("HTTP response stream closed by ZIP writer");
        int count=0;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(output.toByteArray()),StandardCharsets.UTF_8)) {
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()) {
                if(entry.getName().startsWith("/")||entry.getName().contains("../")||entry.getName().contains("\\"))throw new AssertionError(entry.getName());
                count++;eq("PDF"+count,new String(zip.readAllBytes(),StandardCharsets.UTF_8));
            }
        }
        eq(3,count);
        System.out.println("Attachment names and ZIP roundtrip checks passed");
    }
    private static void reject(Runnable r){try{r.run();}catch(IllegalArgumentException e){return;}throw new AssertionError();}
    private static void eq(Object expected,Object actual){if(!java.util.Objects.equals(expected,actual))throw new AssertionError("Expected "+expected+" got "+actual);}
    public static void main(String[] args)throws Exception {run();}
}
