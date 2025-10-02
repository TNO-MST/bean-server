package nl.tno.beanserver;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.http.entity.ContentType;

/**
 *
 * @author bergtwvd
 */
public class BeanClient {

    public static void main(String[] args) throws Exception {
        BeanClient client = new BeanClient();

        client.send();
    }

    public void send() throws Exception {

        //Creating CloseableHttpClient object
        CloseableHttpClient httpclient = HttpClients.createDefault();

        //Creating the MultipartEntityBuilder
        MultipartEntityBuilder entitybuilder = MultipartEntityBuilder.create();

        //Setting the mode
        entitybuilder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);

        //Adding a file
        entitybuilder.addBinaryBody("files", new File("config/foms/RPR_FOM_v2.0_1516-2010.xml"));
        entitybuilder.addTextBody("parm1", "{ \"packageNames\" : [ \"messagefom\" ] }", ContentType.APPLICATION_XML);

        //Building a single entity using the parts
        HttpEntity mutiPartHttpEntity = entitybuilder.build();
        
        //Building the RequestBuilder request object
        RequestBuilder reqbuilder = RequestBuilder.post("http://localhost:7000/upload");

        //Set the entity object to the RequestBuilder
        reqbuilder.setEntity(mutiPartHttpEntity);

        //Building the request
        HttpUriRequest multipartRequest = reqbuilder.build();

        //Executing the request
        CloseableHttpResponse httpresponse = httpclient.execute(multipartRequest);

        //save the result
        copyInputStreamToFile(httpresponse.getEntity().getContent(), new File("my.zip"));

        //Printing the status and the contents of the response
        System.out.println(EntityUtils.toString(httpresponse.getEntity()));

        System.out.println(httpresponse.getStatusLine());
    }

    public static final int DEFAULT_BUFFER_SIZE = 8192;

    private static void copyInputStreamToFile(InputStream inputStream, File file) throws IOException {

        // append = false
        try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
            int read;
            byte[] bytes = new byte[DEFAULT_BUFFER_SIZE];
            while ((read = inputStream.read(bytes)) != -1) {
                outputStream.write(bytes, 0, read);
            }
        }

    }
}
