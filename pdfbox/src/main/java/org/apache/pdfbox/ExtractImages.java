/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pdfbox;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.pdfbox.cos.COSBase;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardDecryptionMaterial;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDXObjectForm;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDXObjectImage;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDJpeg;
import org.apache.pdfbox.io.IOUtils;

/**
 * This will read a read pdf and extract images. <br/><br/>
 *
 * usage: java org.apache.pdfbox.ExtractImages &lt;pdffile&gt; &lt;password&gt; [imageprefix]
 *
 * @author  <a href="mailto:ben@benlitchfield.com">Ben Litchfield</a>
 * @version $Revision: 1.7 $
 */
public class ExtractImages
{
    private int imageCounter = 1;

    private static final String PASSWORD = "-password";
    private static final String PREFIX = "-prefix";
    private static final String ADDKEY = "-addkey";
    private static final String NONSEQ = "-nonSeq";
    private static final String DIRECTJPEG = "-directJPEG";

    private static final List<String> DCT_FILTERS = new ArrayList<String>();

    static
    {
        DCT_FILTERS.add( COSName.DCT_DECODE.getName() );
        DCT_FILTERS.add( COSName.DCT_DECODE_ABBREVIATION.getName() );
    }

    private ExtractImages()
    {
    }

    /**
     * This is the entry point for the application.
     *
     * @param args The command-line arguments.
     *
     * @throws Exception If there is an error decrypting the document.
     */
    public static void main( String[] args ) throws Exception
    {
        ExtractImages extractor = new ExtractImages();
        extractor.extractImages( args );
    }

    private void extractImages( String[] args ) throws Exception
    {
        if( args.length < 1 || args.length > 4 )
        {
            usage();
        }
        else
        {
            String pdfFile = null;
            String password = "";
            String prefix = null;
            boolean addKey = false;
            boolean useNonSeqParser = false;
            boolean directJPEG = false;
            for( int i=0; i<args.length; i++ )
            {
                if( args[i].equals( PASSWORD ) )
                {
                    i++;
                    if( i >= args.length )
                    {
                        usage();
                    }
                    password = args[i];
                }
                else if( args[i].equals( PREFIX ) )
                {
                    i++;
                    if( i >= args.length )
                    {
                        usage();
                    }
                    prefix = args[i];
                }
                else if( args[i].equals( ADDKEY ) )
                {
                    addKey = true;
                }
                else if( args[i].equals( NONSEQ ) )
                {
                    useNonSeqParser = true;
                }
                else if( args[i].equals( DIRECTJPEG ) )
                {
                    directJPEG = true;
                }
                else
                {
                    if( pdfFile == null )
                    {
                        pdfFile = args[i];
                    }
                }
            }
            if(pdfFile == null)
            {
                usage();
            }
            else
            {
                if( prefix == null && pdfFile.length() >4 )
                {
                    prefix = pdfFile.substring( 0, pdfFile.length() -4 );
                }

                PDDocument document = null;

                try
                {
                    if (useNonSeqParser)
                    {
                        document = PDDocument.loadNonSeq(new File(pdfFile), null, password);
                    }
                    else
                    {
                        document = PDDocument.load( pdfFile );
    
                        if( document.isEncrypted() )
                        {
                            StandardDecryptionMaterial spm = new StandardDecryptionMaterial(password);
                            document.openProtection(spm);
                        }
                    }
                    AccessPermission ap = document.getCurrentAccessPermission();
                    if( ! ap.canExtractContent() )
                    {
                        throw new IOException(
                            "Error: You do not have permission to extract images." );
                    }

                    List pages = document.getDocumentCatalog().getAllPages();
                    Iterator iter = pages.iterator();
                    while( iter.hasNext() )
                    {
                        PDPage page = (PDPage)iter.next();
                        PDResources resources = page.getResources();
                        // extract all XObjectImages which are part of the page resources
                        processResources(resources, prefix, addKey, directJPEG, new HashSet<COSBase>());
                    }
                }
                finally
                {
                    if( document != null )
                    {
                        document.close();
                    }
                }
            }
        }
    }
    
    public void writeJpeg2file(PDJpeg image, String filename) throws IOException
    {
        FileOutputStream out = null;
        
        try
        {
            out = new FileOutputStream(filename + ".jpg");
            InputStream data = image.getPDStream().getPartiallyFilteredStream(DCT_FILTERS);
            byte[] buf = new byte[1024];
            int amountRead;
            while ((amountRead = data.read(buf)) != -1)
            {
                out.write(buf, 0, amountRead);
            }
            IOUtils.closeQuietly(data);
            out.flush();
        }
        finally
        {
            if (out != null)
            {
                out.close();
            }
        }
    }

    private void processResources(PDResources resources, String prefix, 
            boolean addKey, boolean directJPEG, Set<COSBase> seen) throws IOException
    {
        if (resources == null)
        {
            return;
        }
        Map<String, PDXObject> xobjects = resources.getXObjects();
        if( xobjects != null )
        {
            Iterator<String> xobjectIter = xobjects.keySet().iterator();
            while( xobjectIter.hasNext() )
            {
                String key = xobjectIter.next();
                PDXObject xobject = xobjects.get( key );
                COSBase cosObject = xobject.getCOSObject();
                if (seen.contains(cosObject))
                {
                    // avoid infinite recursion
                    continue;
                }
                seen.add(cosObject);
                // write the images
                if (xobject instanceof PDXObjectImage)
                {
                    PDXObjectImage image = (PDXObjectImage)xobject;
                    String name = null;
                    if (addKey) 
                    {
                        name = getUniqueFileName( prefix + "_" + key, image.getSuffix() );
                    }
                    else 
                    {
                        name = getUniqueFileName( prefix, image.getSuffix() );
                    }
                    System.out.println( "Writing image:" + name );
                    if (directJPEG && "jpg".equals(image.getSuffix()))
                    {
                        writeJpeg2file((PDJpeg) image, name);
                    }
                    else
                    {
                        image.write2file(name);
                    }
                    image.clear(); // PDFBOX-2101 get rid of cache ASAP
                }
                // maybe there are more images embedded in a form object
                else if (xobject instanceof PDXObjectForm)
                {
                    PDXObjectForm xObjectForm = (PDXObjectForm)xobject;
                    PDResources formResources = xObjectForm.getResources();
                    processResources(formResources, prefix, addKey, directJPEG, seen);
                }
            }
        }
        resources.clear();
    }

    private String getUniqueFileName( String prefix, String suffix )
    {
        String uniqueName = null;
        File f = null;
        while( f == null || f.exists() )
        {
            uniqueName = prefix + "-" + imageCounter;
            f = new File( uniqueName + "." + suffix );
            imageCounter++;
        }
        return uniqueName;
    }

    /**
     * This will print the usage requirements and exit.
     */
    private static void usage()
    {
        System.err.println( "Usage: java org.apache.pdfbox.ExtractImages [OPTIONS] <PDF file>\n" +
            "  -password  <password>        Password to decrypt document\n" +
            "  -prefix  <image-prefix>      Image prefix(default to pdf name)\n" +
            "  -addkey                      add the internal image key to the file name\n" +
            "  -nonSeq                      Enables the new non-sequential parser\n" +
            "  -directJPEG                  Forces the direct extraction of JPEG images regardless of colorspace\n" +
            "  <PDF file>                   The PDF document to use\n"
            );
        System.exit( 1 );
    }

}
