package wt;

import ij.ImagePlus;
import net.imglib2.Cursor;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import bunwarpj.Transformation;
import bunwarpj.bUnwarpJ_;

public class NonrigidAlignment
{
	/**
	 * 
	 * @param img - the target image (to be transformed)
	 * @param t - the Transformation
	 * @param subSampling - image subsampling factor (from 0 to 7, representing 2^0=1 to 2^7 = 128)
	 * @return
	 */
	public static Img< FloatType > transformTarget( final Img< FloatType > img, final Transformation t, final int subSampling )
	{
		final Img< FloatType > transformed = img.factory().create( img, img.firstElement() );

		final Cursor< FloatType > cursor = transformed.localizingCursor();

		final RealRandomAccessible< FloatType > interpolatedArea =
				Views.interpolate( Views.extendZero( img ), new NLinearInterpolatorFactory< FloatType > () );

		final RealRandomAccess< FloatType > i = interpolatedArea.realRandomAccess();

		final double l[] = new double[ img.numDimensions() ];

		final double s = subSampling == 0 ? 1 : Math.pow( 2, subSampling );

		while ( cursor.hasNext() )
		{
			cursor.fwd();

			final double u = cursor.getDoublePosition( 0 ) / s;
			final double v = cursor.getDoublePosition( 1 ) / s;

			t.transform( u, v, l, true );

			l[ 0 ] *= s;
			l[ 1 ] *= s;

			i.setPosition( l );

			cursor.get().set( i.get() );
		}

		return transformed;
	}

	/**
	 * 
	 * @param target - the image to be transformed
	 * @param source - the image that the transformed one is matched to
	 * @param subSampling - image subsampling factor (from 0 to 7, representing 2^0=1 to 2^7 = 128)
	 * @return
	 */
	public static Transformation align( final Img< FloatType > target, final Img< FloatType > source, final int subSampling )
	{
		final ImagePlus targetImp = new ImagePlus( "Target", Alignment.wrap( target ).convertToByteProcessor( true ) );
		final ImagePlus sourceImp = new ImagePlus( "Source", Alignment.wrap( source ).convertToByteProcessor( true ) );

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
		final int img_subsamp_fact = subSampling;
		final int min_scale_deformation = 0;
		final int max_scale_deformation = 4;
		final double divWeight = 0.0;
		final double curlWeight = 0.0;
		final double landmarkWeight = 0.0;
		final double imageWeight = 1.0;
		final double consistencyWeight = 10.0;
		final double stopThreshold = 0.01;
		
		final Transformation t = bUnwarpJ_.computeTransformationBatch(
				targetImp, sourceImp, null, null,
				mode, img_subsamp_fact, min_scale_deformation, max_scale_deformation,
				divWeight, curlWeight, landmarkWeight, imageWeight, consistencyWeight, stopThreshold );

		return t;
	}
}
