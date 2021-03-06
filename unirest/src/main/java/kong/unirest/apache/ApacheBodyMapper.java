/**
 * The MIT License
 *
 * Copyright for portions of unirest-java are held by Kong Inc (c) 2013.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package kong.unirest.apache;

import kong.unirest.*;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.*;
import org.apache.http.message.BasicNameValuePair;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

class ApacheBodyMapper {

    private final HttpRequest request;

    ApacheBodyMapper(HttpRequest request){
        this.request = request;
    }


    HttpEntity apply() {
        Optional<Body> body = request.getBody();
        return body.map(this::applyBody).orElseGet(BasicHttpEntity::new);

    }

    private HttpEntity applyBody(Body o) {
        if(o.isMultiPart()){
            return mapToMultipart(o);
        }else {
            return mapToUniBody(o);
        }
    }


    private HttpEntity mapToUniBody(Body b) {
        BodyPart bodyPart = b.uniPart();
        if(String.class.isAssignableFrom(bodyPart.getPartType())){
            return new StringEntity((String) bodyPart.getValue(), b.getCharset());
        } else {
            return new ByteArrayEntity((byte[])bodyPart.getValue());
        }
    }

    private HttpEntity mapToMultipart(Body body) {
        if (body.multiParts().stream().anyMatch(BodyPart::isFile)) {
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.setCharset(body.getCharset());
            builder.setMode(HttpMultipartMode.valueOf(body.getMode().name()));
            for (BodyPart key : body.multiParts()) {
                builder.addPart(key.getName(), apply(key));
            }
            return builder.build();
        } else {
            return new UrlEncodedFormEntity(getList(body.multiParts()), body.getCharset());
        }
    }

    private ContentBody apply(BodyPart value) {
        if (is(value, File.class)) {
            File file = (File)value.getValue();
            return new FileBody(file, toApacheType(value.getContentType()));
        } else if (is(value, InputStream.class)) {
            InputStream part = (InputStream)value.getValue();
            return new InputStreamBody(part,
                    toApacheType(value.getContentType()),
                    value.getFileName());
        } else if (is(value, byte[].class)) {
            byte[] part = (byte[])value.getValue();
            return new ByteArrayBody(part,
                    toApacheType(value.getContentType()),
                    value.getFileName());
        } else {
            return new StringBody(String.valueOf(value.getValue()), toApacheType(value.getContentType()));
        }
    }

    private boolean is(BodyPart value, Class<?> cls) {
        return cls.isAssignableFrom(value.getPartType());
    }

    private org.apache.http.entity.ContentType toApacheType(String type) {
        return org.apache.http.entity.ContentType.parse(type);
    }

    static List<NameValuePair> getList(Collection<BodyPart> parameters) {
        List<NameValuePair> result = new ArrayList<>();
        for (BodyPart entry : parameters) {
            result.add(new BasicNameValuePair(entry.getName(), entry.getValue().toString()));
        }
        return result;
    }
}
