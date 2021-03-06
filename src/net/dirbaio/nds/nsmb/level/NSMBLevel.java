/*
 *   This file instanceof part of NSMB Editor 5.
 *
 *   NSMB Editor 5 instanceof free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License  by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   NSMB Editor 5 instanceof distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with NSMB Editor 5.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.dirbaio.nds.nsmb.level;

import java.awt.Rectangle;
import java.util.ArrayList;
import net.dirbaio.nds.nsmb.NSMBRom;
import net.dirbaio.nds.fs.AlreadyEditingException;
import net.dirbaio.nds.nsmb.level.source.LevelData;
import net.dirbaio.nds.nsmb.level.source.LevelIOException;
import net.dirbaio.nds.nsmb.level.source.LevelSource;
import net.dirbaio.nds.util.ArrayReader;
import net.dirbaio.nds.util.ArrayWriter;

public class NSMBLevel
{
    public static final int WIDTH = 512;
    public static final int HEIGHT = 256;

    public LevelSource source;
    public String name;
    public byte[][] Blocks;
    public LevelObjects objs = new LevelObjects();
    public NSMBGraphics GFX;
    public boolean[] ValidSprites;
    public int[][] levelTilemap = new int[WIDTH][HEIGHT];
    NSMBRom rom;

    public NSMBLevel(NSMBRom rom, LevelSource source) throws LevelIOException
    {
        this.rom = rom;
        this.source = source;
        this.name = source.getName();
        LevelData data = source.getData();
        byte[] eLevelFile = data.LevelFile;
        byte[] eBGFile = data.BGDatFile;

        // Level loading time yay.
        Blocks = new byte[14][];

        ArrayReader in = new ArrayReader(eLevelFile);
        for (int i = 0; i < 14; i++)
        {
            int BlockOffset = in.readInt();
            int BlockSize = in.readInt();

            Blocks[i] = new byte[BlockSize];
            System.arraycopy(eLevelFile, BlockOffset, Blocks[i], 0, BlockSize);
        }

        byte tilesetId = Blocks[0][0x0C];
        byte bgId = Blocks[2][2];
        GFX = new NSMBGraphics(rom);
        GFX.LoadTilesets(tilesetId, bgId);

        //Objects.
        in = new ArrayReader(eBGFile);
        while (in.available(10))
            objs.Objects.add(NSMBObject.read(in, GFX));

        // Sprites
        in = new ArrayReader(Blocks[6]);
        while (in.available(12))
            objs.Sprites.add(NSMBSprite.read(this, in));

        // Entrances.
        in = new ArrayReader(Blocks[5]);
        while (in.available(20))
            objs.Entrances.add(NSMBEntrance.read(in));

        // Views
        in = new ArrayReader(Blocks[7]);
        ArrayReader camin = new ArrayReader(Blocks[1]);
        while (in.available(16))
            objs.Views.add(NSMBView.read(in, camin));

        // Zones
        in = new ArrayReader(Blocks[8]);
        while (in.available(12))
            objs.Zones.add(NSMBView.readZone(in));

        // Paths
        in = new ArrayReader(Blocks[10]);
        ArrayReader nodein = new ArrayReader(Blocks[12]);

        while (in.available(1))
            objs.Paths.add(NSMBPath.read(in, nodein, false));

        //Progress paths
        in = new ArrayReader(Blocks[9]);
        nodein = new ArrayReader(Blocks[11]);

        while (in.available(1))
            objs.ProgressPaths.add(NSMBPath.read(in, nodein, true));

        //Stuff
        CalculateSpriteModifiers();
        ReRenderAll();
        repaintAllTilemap();
    }

    public void repaintAllTilemap()
    {
        repaintTilemap(0, 0, WIDTH, HEIGHT);
    }

    public void repaintTilemap(int x, int y, int w, int h)
    {
        if (w == 0)
            return;
        if (h == 0)
            return;
        if (x < 0)
            x = 0;
        if (y < 0)
            y = 0;
        if (x + w > WIDTH)
            w = WIDTH - x;
        if (y + h > HEIGHT)
            h = HEIGHT - y;

        for (int xx = 0; xx < w; xx++)
            for (int yy = 0; yy < h; yy++)
                levelTilemap[xx + x][yy + y] = 0;

        Rectangle r = new Rectangle(x, y, w, h);

        for (NSMBObject o : objs.Objects)
        {
            Rectangle ObjRect = new Rectangle(o.X, o.Y, o.Width, o.Height);
            if (ObjRect.intersects(r))
                o.renderTilemap(levelTilemap, r);
        }
    }

    public void open() throws AlreadyEditingException
    {
        source.open();
    }

    public void close()
    {
        source.close();
    }

    public void Save() throws LevelIOException
    {
        LevelData exlvl = getExport();
        source.setData(exlvl);
    }

    private LevelData getExport()
    {
        // Objects
        ArrayWriter out = new ArrayWriter();

        for (NSMBObject o : objs.Objects)
            o.write(out);

        out.writeShort((short) 0xFFFF);
        byte[] bgDatFileData = out.getArray();

        //Sprites
        out = new ArrayWriter();
        for (NSMBSprite s : objs.Sprites)
            s.write(out);
        out.writeInt(0xFFFFFFFF); //Is this necessary? lul
        Blocks[6] = out.getArray();

        //Entrances
        out = new ArrayWriter();
        for (NSMBEntrance e : objs.Entrances)
            e.write(out);
        Blocks[5] = out.getArray();

        //Paths
        ArrayWriter block11 = new ArrayWriter();
        ArrayWriter block13 = new ArrayWriter();
        for (NSMBPath p : objs.Paths)
            p.write(block11, block13);

        Blocks[10] = block11.getArray(); //save streams
        Blocks[12] = block13.getArray();

        //ProgressPaths

        out = new ArrayWriter();
        ArrayWriter nodeout = new ArrayWriter();
        for (NSMBPath p : objs.ProgressPaths)
            p.write(out, nodeout);

        Blocks[9] = out.getArray(); //save streams
        Blocks[11] = nodeout.getArray();

        //Views

        out = new ArrayWriter();
        nodeout = new ArrayWriter();
        int camCount = 0;
        for (NSMBView v : objs.Views)
            v.write(out, nodeout, camCount++);
        Blocks[7] = out.getArray();
        Blocks[1] = nodeout.getArray();

        //save Zones
        out = new ArrayWriter();
        for (NSMBView v : objs.Zones)
            v.writeZone(out);
        Blocks[8] = out.getArray();

        //Level blocks
        int LevelFileSize = 8 * 14;

        // Find out how long the file must be
        for (int BlockIdx = 0; BlockIdx < 14; BlockIdx++)
            LevelFileSize += Blocks[BlockIdx].length;

        out = new ArrayWriter();

        // Now allocate + save it
        int currOffs = 8 * 14;

        for (int i = 0; i < 14; i++)
        {
            out.writeInt(currOffs);
            out.writeInt(Blocks[i].length);
            currOffs += Blocks[i].length;
        }

        for (int i = 0; i < 14; i++)
            out.write(Blocks[i]);

        byte[] levelFileData = out.getArray();

        return new LevelData(levelFileData, bgDatFileData);
    }

    public void ReRenderAll()
    {
        for (NSMBObject o : objs.Objects)
            o.UpdateObjCache();
    }

    public void CalculateSpriteModifiers()
    {
        ValidSprites = new boolean[NSMBRom.spriteCount];

        for (int idx = 0; idx < NSMBRom.spriteCount; idx++)
        {
            int val = rom.getTable(NSMBRom.Table.Table_Modifiers).getVal(idx);
            int ModifierOffset = val & 0xFF;
            int ModifierValue = (val >> 8) & 0xFF;
            if (ModifierValue == 0)
                ValidSprites[idx] = true;
            else // works around levels like 1-4 area 2 which have a blank modifier block
            if (Blocks[13].length > 0 && Blocks[13][ModifierOffset] == ModifierValue)
                ValidSprites[idx] = true;
        }
    }

    public int getFreeEntranceNumber()
    {
        int n = 0;

        while (true)
        {
            if (!isEntranceNumberUsed(n))
                return n;
            n++;
        }
    }

    public int getFreeViewNumber(ArrayList<NSMBView> l)
    {
        int n = 0;

        while (true)
        {
            if (!isViewNumberUsed(n, l))
                return n;
            n++;
        }
    }

    public int getFreePathNumber(ArrayList<NSMBPath> l, int startID)
    {
        int n = startID;

        while (true)
        {
            if (!isPathNumberUsed(n, l))
                return n;
            n++;
        }
    }

    public boolean isEntranceNumberUsed(int n)
    {
        for (NSMBEntrance e : objs.Entrances)
            if (e.Number == n)
                return true;

        return false;
    }

    public boolean isViewNumberUsed(int n, ArrayList<NSMBView> l)
    {
        for (NSMBView e : l)
            if (e.Number == n)
                return true;

        return false;
    }

    public boolean isPathNumberUsed(int n, ArrayList<NSMBPath> l)
    {
        for (NSMBPath p : l)
            if (p.id == n)
                return true;

        return false;
    }
}
