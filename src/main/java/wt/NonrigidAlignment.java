package wt;

import bunwarpj.Transformation;
import bunwarpj.bUnwarpJ_;
import ij.ImagePlus;
import ij.process.ByteProcessor;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;

public class NonrigidAlignment
{
	public NonrigidAlignment( final Img< FloatType > imgA, final Img< FloatType > imgB )
	{
		final ByteProcessor bpA = Alignment.wrap( imgA ).convertToByteProcessor( true );
		final ByteProcessor bpB = Alignment.wrap( imgB ).convertToByteProcessor( true );

		final ImagePlus impA = new ImagePlus( "A", bpA );
		final ImagePlus impB = new ImagePlus( "B", bpB );

		impA.show();
		impB.show();

		/*
		 * targetImp - input target image
		 * sourceImp - input source image
		 * targetMskIP - target mask
		 * sourceMskIP - source mask
		 * mode - accuracy mode (0 - Fast, 1 - Accurate, 2 - Mono)
		 * img_subsamp_fact - image subsampling factor (from 0 to 7, representing 2^0=1 to 2^7 = 128)
		 * min_scale_deformation - (0 - Very Coarse, 1 - Coarse, 2 - Fine, 3 - Very Fine)
		 * max_scale_deformation - (0 - Very Coarse, 1 - Coarse, 2 - Fine, 3 - Very Fine, 4 - Super Fine)
		 * divWeight - divergence weight
		 * curlWeight - curl weight
		 * landmarkWeight - landmark weight
		 * imageWeight - image similarity weight
		 * consistencyWeight - consistency weight
		 * stopThreshold - stopping threshold
		 */
		
		final int mode = 0;
		final int img_subsamp_fact = 1;
		final int min_scale_deformation = 0;
		final int max_scale_deformation = 2;
		final double divWeight = 0.0;
		final double curlWeight = 0.0;
		final double landmarkWeight = 0.0;
		final double imageWeight = 1.0;
		final double consistencyWeight = 10.0;
		final double stopThreshold = 0.01;
		
		final Transformation t = bUnwarpJ_.computeTransformationBatch(
				impB, impA, null, null,
				mode, img_subsamp_fact, min_scale_deformation, max_scale_deformation,
				divWeight, curlWeight, landmarkWeight, imageWeight, consistencyWeight, stopThreshold );

		System.out.println( t.toString() );
	}
}
