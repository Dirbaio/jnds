/*
 * Copyright (C) 2013 dirbaio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.dirbaio.nds.util;   

import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JScrollPane;

public class ComponentFrame extends JFrame
{
    public ComponentFrame(JComponent i)
    {
        this(i, false);
    }

    public ComponentFrame(JComponent i, boolean scrollPane)
    {
        super(i.toString());
        setSize(800, 500);
        if(scrollPane)
        {
            JScrollPane jsp = new JScrollPane(i);
            jsp.setBorder(null);
            add(jsp, BorderLayout.CENTER);
        }
        else
            add(i, BorderLayout.CENTER);
        
        pack();
        setVisible(true);
    }
}