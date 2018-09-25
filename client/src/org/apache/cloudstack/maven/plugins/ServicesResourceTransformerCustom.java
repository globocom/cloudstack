package org.apache.cloudstack.maven.plugins;

import com.google.common.io.LineReader;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugins.shade.relocation.Relocator;
import org.apache.maven.plugins.shade.resource.ResourceTransformer;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public class ServicesResourceTransformerCustom implements ResourceTransformer
{

    //configuration
    private String resource;

    private Map<String, ServiceStream> serviceEntries = new HashMap<>();

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }


    public boolean canTransformResource( String resource )
    {
        if ( this.resource.startsWith( resource ) )
        {
            return true;
        }

        return false;
    }

    public void processResource(String resource, InputStream is, final List<Relocator> relocators )
            throws IOException
    {
        if (getResource().equals(resource)) {
            ServiceStream out = serviceEntries.get( resource );
            if ( out == null )
            {
                out = new ServiceStream();
                serviceEntries.put( resource, out );
            }

            final ServiceStream fout = out;

            final String content = IOUtils.toString( is );
            StringReader reader = new StringReader( content );
            LineReader lineReader = new LineReader( reader );
            String line;
            while ( ( line = lineReader.readLine() ) != null )
            {
                fout.append( line );
            }
        }

    }
    public boolean hasTransformedResource()
    {
        return serviceEntries.size() > 0;
    }

    public void modifyOutputStream( JarOutputStream jos ) throws IOException
    {
        for ( Map.Entry<String, ServiceStream> entry : serviceEntries.entrySet() )
        {
            String key = entry.getKey();
            ServiceStream data = entry.getValue();

            jos.putNextEntry( new JarEntry( key ) );

            PrintWriter writer = new PrintWriter( jos );
            InputStreamReader streamReader = new InputStreamReader( data.toInputStream() );
            BufferedReader reader = new BufferedReader( streamReader );
            String className;

            while ( ( className = reader.readLine() ) != null )
            {
                writer.println( className );
                writer.flush();
            }

            reader.close();
            data.reset();
        }
    }

    static class ServiceStream extends ByteArrayOutputStream
    {

        public ServiceStream()
        {
            super( 1024 );
        }

        public void append( String content ) throws IOException
        {
            write( ',' );
            byte[] contentBytes = content.getBytes( "UTF-8" );
            this.write( contentBytes );
        }

        public InputStream toInputStream()
        {
            return new ByteArrayInputStream( buf, 0, count );
        }

    }

}