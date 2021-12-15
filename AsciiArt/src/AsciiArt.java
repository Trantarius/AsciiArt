import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class AsciiArt {
    public static int[][] unicodeRanges=new int[][]{{0x20,0x7f}};
    public static char[] chars;//={'a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z'};
    public static BufferedImage[] charImages;
    public static double[] shades;
    public static int fontSize=8;
    public static int[] patchSize;
    public static int rowOffset;
    public static boolean antialiasing=true;
    public static String filepath="sign.png";
    public static String outputPath="out.png";
    public static Font font=new Font(Font.MONOSPACED,Font.PLAIN,fontSize);
    public static void main(String[] args) throws IOException, InterruptedException {
        BufferedImage dummyImg=new BufferedImage(8,8,BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D dummyGraphics=dummyImg.createGraphics();
        dummyGraphics.setFont(font);
        if(antialiasing){
            dummyGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }
        Rectangle2D charRect=font.getMaxCharBounds(dummyGraphics.getFontRenderContext());
        patchSize=new int[]{(int)Math.round(charRect.getWidth()),(int)Math.round(charRect.getHeight())};
        rowOffset=-(int)Math.round(charRect.getY());

        //make char list
        int numchars=0;
        for(int n=0;n<unicodeRanges.length;n++){
            numchars+=unicodeRanges[n][1]-unicodeRanges[n][0];
        }
        chars=new char[numchars];
        shades= new double[chars.length];
        int idx=0;
        for(int n=0;n<unicodeRanges.length;n++){
            for(int c=0;c<unicodeRanges[n][1]-unicodeRanges[n][0];c++){
                chars[idx]=(char)(unicodeRanges[n][0]+c);
                idx++;
            }
        }
        System.out.print("using: ");
        System.out.println(chars);


        //make an image for each char
        charImages=new BufferedImage[chars.length];
        for(int n=0;n<chars.length;n++){
            charImages[n]=new BufferedImage(patchSize[0],patchSize[1],BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g=charImages[n].createGraphics();
            g.setFont(font);
            if(antialiasing)
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.drawChars(new char[]{chars[n]},0,1,0,rowOffset);
            shades[n]=0;
            for(int x=0;x<patchSize[0];x++){
                for(int y=0;y<patchSize[1];y++){
                    shades[n]+=charImages[n].getRGB(x,y)&0xff;
                }
            }
        }
        BufferedImage inputImg= ImageIO.read(new File(filepath));
        BufferedImage grayImg=new BufferedImage(inputImg.getWidth(),inputImg.getHeight(),BufferedImage.TYPE_BYTE_GRAY);
        for(int x=0;x< inputImg.getWidth();x++){
            for(int y=0;y< inputImg.getHeight();y++){
                grayImg.setRGB(x,y,inputImg.getRGB(x,y));
            }
        }
        popImage(grayImg);


        //find optimal char for each patch
        long start=System.currentTimeMillis();
        char[][] ascii=new char[grayImg.getWidth()/patchSize[0]][grayImg.getHeight()/patchSize[1]];
        Thread[] threads=new Thread[ascii.length];
        for(int px=0;px<ascii.length;px++){
            int finalPx = px;
            threads[px]=new Thread(()->{
                for(int py=0;py<ascii[0].length;py++){
                    double[] err=new double[chars.length];
                    double[] shadeErr=new double[chars.length];
                    for(int n=0;n<chars.length;n++){
                        err[n]=0;
                        double pshade=0;
                        for(int dx=0;dx<patchSize[0];dx++){
                            for(int dy=0;dy<patchSize[1];dy++){
                                int temp=(grayImg.getRGB(finalPx *patchSize[0]+dx,py*patchSize[1]+dy)&0xff) -
                                        (charImages[n].getRGB(dx,dy)&0xff);
                                pshade+=grayImg.getRGB(finalPx *patchSize[0]+dx,py*patchSize[1]+dy)&0xff;
                                err[n]+=(double)temp*temp/255;
                            }
                        }
                        shadeErr[n]=Math.abs(shades[n]-pshade/2);
                        err[n]+=shadeErr[n]/2;
                    }
                    double minerr=err[0];
                    int minIdx=0;
                    for(int n=1;n<err.length;n++){
                        if(err[n]<minerr){
                            minerr=err[n];
                            minIdx=n;
                        }
                    }
                    ascii[finalPx][py]=chars[minIdx];
                }
            });
            threads[px].start();
        }
        for(int n=0;n<threads.length;n++){
            threads[n].join();
        }
        System.out.println("finished in "+(System.currentTimeMillis()-start)+"ms");

        //create image from chars in ascii[][]
        BufferedImage asciiImg=new BufferedImage(grayImg.getWidth(),grayImg.getHeight(),BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g=asciiImg.createGraphics();
        g.setFont(font);
        if(antialiasing)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for(int px=0;px<ascii.length;px++) {
            for (int py = 0; py < ascii[0].length; py++) {
                g.drawChars(new char[]{ascii[px][py]},0,1,px*patchSize[0],py*patchSize[1]+rowOffset);
            }
        }
        popImage(asciiImg);

        ImageIO.write(asciiImg,"png",new File(outputPath));

        //print chars in ascii[][]
        for(int y=0;y<ascii[0].length;y++){
            for(int x=0;x<ascii.length;x++){
                System.out.print(ascii[x][y]);
            }
            System.out.print("\n");
        }
    }

    public static void popImage(BufferedImage img){
        JFrame frame=new JFrame();
        frame.setSize(img.getWidth()+32,img.getHeight()+32);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(new JPanel(){
           public void paintComponent(Graphics g){
               g.drawImage(img,0,0,this);
           }
        });
        frame.setVisible(true);
    }
}
