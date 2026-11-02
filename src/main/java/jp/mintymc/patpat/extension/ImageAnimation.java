package jp.mintymc.patpat.extension;

import javax.imageio.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import org.w3c.dom.Node;

/** Decodes and composites GIF disposal/offsets before packing independent PNG textures. */
record ImageAnimation(List<BufferedImage> frames,List<Integer> delays) {
    static final int MAX_FRAMES=128;
    ImageAnimation { frames=List.copyOf(frames);delays=List.copyOf(delays); }
    long duration(){return delays.stream().mapToLong(Integer::longValue).sum();}
    int frameAt(long elapsed,boolean loop) {
        long total=duration();long time=loop ? Math.floorMod(elapsed,total) : Math.min(Math.max(0,elapsed),total-1);
        for(int i=0;i<delays.size();i++){if(time<delays.get(i))return i;time-=delays.get(i);}
        return delays.size()-1;
    }
    static ImageAnimation load(Path path) throws IOException {
        if(!Files.isRegularFile(path) || Files.size(path)>16*1024*1024)throw new IOException("Image must exist and be at most 16 MiB: "+path.getFileName());
        try(ImageInputStream input=ImageIO.createImageInputStream(path.toFile())) {
            if(input==null)throw new IOException("Unable to open image");
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("Use PNG or GIF");
            ImageReader reader=readers.next();
            try {
                reader.setInput(input,false,false);String format=reader.getFormatName();
                if(!format.equalsIgnoreCase("png")&&!format.equalsIgnoreCase("gif"))throw new IOException("Use PNG or GIF");
                int count=reader.getNumImages(true);if(count<1||count>MAX_FRAMES)throw new IOException("Use 1..128 frames");
                int width=reader.getWidth(0),height=reader.getHeight(0);
                Node stream=tree(reader.getStreamMetadata(),"javax_imageio_gif_stream_1.0");
                Node screen=child(stream,"LogicalScreenDescriptor");
                if(screen!=null){width=attrInt(screen,"logicalScreenWidth",width);height=attrInt(screen,"logicalScreenHeight",height);}
                if(width<1||height<1||width>1024||height>1024)throw new IOException("Image canvas must be 1..1024 pixels per axis");
                BufferedImage canvas=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
                List<BufferedImage> frames=new ArrayList<>();List<Integer> delays=new ArrayList<>();
                for(int i=0;i<count;i++) {
                    Node metadata=tree(reader.getImageMetadata(i),"javax_imageio_gif_image_1.0");
                    Node descriptor=child(metadata,"ImageDescriptor"),control=child(metadata,"GraphicControlExtension");
                    int x=attrInt(descriptor,"imageLeftPosition",0),y=attrInt(descriptor,"imageTopPosition",0);
                    String disposal=attr(control,"disposalMethod","none");
                    BufferedImage saved=disposal.equals("restoreToPrevious")?copy(canvas):null;
                    BufferedImage frame=reader.read(i);
                    Graphics2D g=canvas.createGraphics();g.setComposite(AlphaComposite.SrcOver);g.drawImage(frame,x,y,null);g.dispose();
                    frames.add(fit(canvas));
                    int delay=format.equalsIgnoreCase("gif")?attrInt(control,"delayTime",10)*10:800;
                    // The network renderer updates at 20 Hz; zero-delay GIF frames use 100 ms.
                    delays.add(delay<=0?100:Math.clamp(delay,50,10000));
                    if(disposal.equals("restoreToBackgroundColor")) {
                        g=canvas.createGraphics();g.setComposite(AlphaComposite.Clear);g.fillRect(x,y,frame.getWidth(),frame.getHeight());g.dispose();
                    } else if(saved!=null)canvas=saved;
                }
                return new ImageAnimation(frames,delays);
            } finally {reader.dispose();}
        }
    }
    private static BufferedImage fit(BufferedImage input) {
        int side=128;double ratio=Math.min((double)side/input.getWidth(),(double)side/input.getHeight());
        int w=Math.max(1,(int)Math.round(input.getWidth()*ratio)),h=Math.max(1,(int)Math.round(input.getHeight()*ratio));
        BufferedImage output=new BufferedImage(side,side,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=output.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(input,(side-w)/2,(side-h)/2,w,h,null);g.dispose();return output;
    }
    private static BufferedImage copy(BufferedImage input) {
        BufferedImage result=new BufferedImage(input.getWidth(),input.getHeight(),BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=result.createGraphics();g.drawImage(input,0,0,null);g.dispose();return result;
    }
    private static Node tree(IIOMetadata metadata,String name) {
        if(metadata==null)return null;
        try{return metadata.getAsTree(name);}catch(IllegalArgumentException e){return null;}
    }
    private static Node child(Node node,String name) {
        if(node==null)return null;
        for(Node n=node.getFirstChild();n!=null;n=n.getNextSibling())if(n.getNodeName().equals(name))return n;
        return null;
    }
    private static String attr(Node node,String name,String fallback) {
        if(node==null||node.getAttributes()==null)return fallback;
        Node n=node.getAttributes().getNamedItem(name);return n==null?fallback:n.getNodeValue();
    }
    private static int attrInt(Node node,String name,int fallback) {
        try{return Integer.parseInt(attr(node,name,Integer.toString(fallback)));}catch(NumberFormatException e){return fallback;}
    }
}
