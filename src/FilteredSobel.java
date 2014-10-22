import com.github.sarxos.webcam.Webcam;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * Real-time(ish) sobel edge detection, pre-processed with median noise filter
 *
 * Quite slow, single-threaded and using 2D sobel. little optimisation, i get ~1FPS @ 640x480.
 *
 * playing with the median filter radius and adding thresholding to the sobel gradient magnitude can get better edges.
 *
 * Created by joe on 21/10/14.
 */
public class FilteredSobel {

    JFrame frame;
    JLabel label;
    ImageIcon image;

    final int IMAGE_WIDTH = 640;
    final int IMAGE_HEIGHT = 480;

    public static void main(String args[]){
        FilteredSobel sc = new FilteredSobel();
    }


    public FilteredSobel(){
        frame = new JFrame();
        image = new ImageIcon();
        label = new JLabel(image);

        frame.add(label);
        frame.setResizable(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setSize(new Dimension(IMAGE_WIDTH, IMAGE_HEIGHT));
        frame.setVisible(true);
        frame.setTitle(String.format("Webcam Edge-detection - (%sx%s) @ 0FPS", IMAGE_WIDTH, IMAGE_HEIGHT));

        this.updateImageIcon();
    }

    public void updateImageIcon(){
        Webcam w = Webcam.getDefault();
        w.setViewSize(new Dimension(IMAGE_WIDTH, IMAGE_HEIGHT));
        w.open();
        while(true){
            long startTime = System.nanoTime();
            this.image.setImage(processImage(w.getImage()));
            label.repaint();
            double fps = 1000000000.0/(System.nanoTime() - startTime);
            frame.setTitle(String.format("Webcam Edge-detection - (%sx%s) @ %.3fFPS", IMAGE_WIDTH, IMAGE_HEIGHT, fps));
        }
    }


    //median filter +/- (gridSize/2) around each pixel.
    public BufferedImage removeNoise(BufferedImage inputImage, int gridSize){
        BufferedImage image = new BufferedImage(inputImage.getWidth(null), inputImage.getHeight(null), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D bGr = image.createGraphics();
        bGr.drawImage(inputImage, 0, 0, null);
        bGr.dispose();

        BufferedImage outputImage = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());

        if(gridSize%2 == 0){
            gridSize += 1;
        }

        int minSafeX = (int)Math.floor(gridSize/2); // grid size = 9,  +/- 4 from given pixel
        int maxSafeX = outputImage.getWidth() - (int)Math.floor(gridSize/2);
        int minSafeY = (int)Math.floor(gridSize/2);
        int maxSafeY = outputImage.getHeight() - (int)Math.floor(gridSize/2);

        int normalisedGridMin = -1 * (int)Math.floor(gridSize/2);
        int normalisedGridMax =  gridSize + normalisedGridMin;

        for(int y = minSafeY; y < maxSafeY; y++) {
            for (int x = minSafeX; x < maxSafeX; x++) {
                int[] values = new int[gridSize*gridSize];
                int count = 0;
                for(int i = normalisedGridMin; i<normalisedGridMax; i++){
                    for(int j = normalisedGridMin; j < normalisedGridMax; j++){
                        values[count++] = new Color(image.getRGB(x+j, y+i)).getBlue();
                    }
                }

                Arrays.sort(values);
                int mean = values[values.length/2];
                outputImage.setRGB(x, y, new Color(mean, mean, mean).getRGB());
            }
        }


        return outputImage;
    }

    public BufferedImage processImage(Image inputImage) {
        BufferedImage image = new BufferedImage(inputImage.getWidth(null), inputImage.getHeight(null), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D bGr = image.createGraphics();
        bGr.drawImage(inputImage, 0, 0, null);
        bGr.dispose();

        BufferedImage sobelImage = new BufferedImage(image.getWidth()-1,image.getHeight()-1, image.getType());
        BufferedImage xImage = new BufferedImage(sobelImage.getWidth(), sobelImage.getHeight(), sobelImage.getType());
        BufferedImage yImage = new BufferedImage(sobelImage.getWidth(), sobelImage.getHeight(), sobelImage.getType());

        int[][] yKernel = {{1, 2, 1}, {0, 0, 0}, {-1, -2, -1}};
        int[][] xKernel = {{-1, 0, 1}, {-2, 0, 2}, {-1, 0, 1}};

        image = removeNoise(image, 3); //de-noise image a bit, using median filter

        //convolve with x kernel
        for(int y = 1; y < image.getHeight()-1; y++){
            for(int x = 1; x < image.getWidth()-1; x++){
                int pVal = 0;

                for(int row = 0; row < xKernel.length; row++){
                    for(int col = 0; col < xKernel[row].length; col++){
                        pVal += (new Color(image.getRGB(x+col-1, y+row-1))).getBlue() * xKernel[col][row];
                    }
                }

                int pValLim = Math.abs(pVal) > 255 ? 255 : Math.abs(pVal);
                xImage.setRGB(x, y, (new Color(pValLim, pValLim, pValLim)).getRGB());
            }
        }

        //convolve with y kernel
        for(int y = 1; y < image.getHeight()-1; y++){
            for(int x = 1; x < image.getWidth()-1; x++){
                int pVal = 0;

                for(int row = 0; row < yKernel.length; row++){
                    for(int col = 0; col < yKernel[row].length; col++){
                        pVal += (new Color(image.getRGB(x+col-1, y+row-1))).getBlue() * yKernel[col][row];
                    }
                }

                int pValLim = Math.abs(pVal) > 255 ? 255 : Math.abs(pVal);
                yImage.setRGB(x, y, (new Color(pValLim, pValLim, pValLim)).getRGB());
            }
        }

        //Edge Magnitude
        for(int y = 0; y < xImage.getHeight(); y++){
            for(int x = 0; x < xImage.getWidth(); x++){
                int squared = (int)Math.abs(Math.pow((new Color(xImage.getRGB(x, y))).getBlue(), 2) - (int)Math.abs(Math.pow((new Color(yImage.getRGB(x, y))).getBlue(), 2)));
                int val = (int)Math.sqrt(squared);
                int valLim = val > 255 ? 255 : val;
                sobelImage.setRGB(x,y, (new Color(255-valLim, 255-valLim, 255-valLim)).getRGB());
            }
        }


        return sobelImage;

    }

}
