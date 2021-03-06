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
package org.apache.pdfbox.pdfviewer;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

import java.io.IOException;

import javax.swing.JPanel;

import org.apache.pdfbox.pdmodel.PDPage;

import org.apache.pdfbox.pdmodel.common.PDRectangle;

/**
 * This is a simple JPanel that can be used to display a PDF page.
 *
 * @author <a href="mailto:ben@benlitchfield.com">Ben Litchfield</a>
 * @version $Revision: 1.4 $
 */
public class PDFPagePanel extends JPanel
{

    private static final long serialVersionUID = -4629033339560890669L;
    
    private PDPage page;
    private PageDrawer drawer = null;
    private Dimension pageDimension = null;
    private Dimension drawDimension = null;

    /**
     * Constructor.
     *
     * @throws IOException If there is an error creating the Page drawing objects.
     */
    public PDFPagePanel() throws IOException
    {
        drawer = new PageDrawer();
    }

    /**
     * This will set the page that should be displayed in this panel.
     *
     * @param pdfPage The page to draw.
     */
    public void setPage( PDPage pdfPage )
    {
        page = pdfPage;
        PDRectangle cropBox = page.findCropBox();
        drawDimension = cropBox.createDimension();
        if (page.findRotation() % 180 == 0)
        {
            pageDimension = drawDimension;
        }
        else
        {
            pageDimension = new Dimension(drawDimension.height, drawDimension.width);
        }
        setSize( pageDimension );
        setBackground( java.awt.Color.white );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void paint(Graphics g )
    {
        try
        {
            g.setColor( getBackground() );
            g.fillRect( 0, 0, getWidth(), getHeight() );

            int rotationAngle = page.findRotation();
            // normalize the rotation angle
            while (rotationAngle < 0)
            {
                rotationAngle += 360;
            }
            while (rotationAngle >= 360)
            {
                rotationAngle -= 360;
            }
            if (rotationAngle != 0)
            {
                Graphics2D g2D = (Graphics2D)g;
                double translateX = 0;
                double translateY = 0;
                switch (rotationAngle)
                {
                    case 90:
                        translateX = pageDimension.getWidth();
                        break;
                    case 270:
                        translateY = pageDimension.getHeight();
                        break;
                    case 180:
                        translateX = pageDimension.getWidth();
                        translateY = pageDimension.getHeight();
                        break;
                }
                g2D.translate(translateX, translateY);
                g2D.rotate(Math.toRadians(rotationAngle));
            }
            drawer.drawPage( g, page, drawDimension );
        }
        catch( IOException e )
        {
            e.printStackTrace();
        }
    }
}
