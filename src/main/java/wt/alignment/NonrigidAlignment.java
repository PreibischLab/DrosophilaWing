package wt.alignment;

import org.jruby.RubyProcess.Sys;

import ij.ImagePlus;
import mpicbg.models.AbstractAffineModel2D;
import mpicbg.models.NoninvertibleModelException;
import net.imglib2.Cursor;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import spim.process.fusion.FusionHelper;
import bunwarpj.Transformation;
import bunwarpj.bUnwarpJ_;

public class NonrigidAlignment
{
	String log = "";

	public String log() { return log; }

	public Img< FloatType > transformAll( final Img< FloatType > img, final AbstractAffineModel2D< ? > model, final long[] offset, final Transformation t, final int subSampling )
	{
		final Img< FloatType > out = img.factory().create( img, img.firstElement() );

		final Cursor< FloatType > cursor = out.localizingCursor();

		final RealRandomAccess< FloatType > interpolate =
				Views.interpolate( Views.extendZero( img ), new NLinearInterpolatorFactory< FloatType >() ).realRandomAccess();

		final double[] l1 = new double[ out.numDimensions() ];
		final float[] l2 = new float[ out.numDimensions() ];

		final double s = subSampling == 0 ? 1 : Math.pow( 2, subSampling );

		try
		{

			while ( cursor.hasNext() )
			{
				final FloatType f = cursor.next();

				final double u = ( cursor.getDoublePosition( 0 ) + offset[ 0 ] ) / s;
				final double v = ( cursor.getDoublePosition( 1 ) + offset[ 1 ] ) / s;

				t.transform( u, v, l1, true );

				l2[ 0 ] = (float)( l1[ 0 ] * s - offset[ 0 ] );
				l2[ 1 ] = (float)( l1[ 1 ] * s - offset[ 1 ] );

				model.applyInverseInPlace( l2 );

				interpolate.setPosition( l2 );

				f.set( interpolate.get() );
			}
		} 
		catch (NoninvertibleModelException e) { e.printStackTrace(); }

		return out;
	}

	/**
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

	public static String adjustImageIntensities(
			final Img< FloatType > target,
			final Img< FloatType > source,
			final double targetOutOfBoundsValue,
			final double sourceOutOfBoundsValue )
	{
		final float[] minMaxTarget = FusionHelper.minMax( target );
		final float[] minMaxSource = FusionHelper.minMax( source );

		String s =
				"Source: extend value " + sourceOutOfBoundsValue + ", minmax=" + Util.printCoordinates( minMaxSource ) + "\n" +
				"Target: extend value " + targetOutOfBoundsValue + ", minmax=" + Util.printCoordinates( minMaxTarget ) + "\n";

		// linear mapping target from [min2,oobs2,max2] to [0,f(oobs2),255]
		// f(z) = a2*z + n2
		// quadratic mapping source from [min1,oobs1,max1] to [0,g(oobs1)=f(oobs2),255]
		// g(x) = a1*x*x + b1*x + c1
		final double z1 = minMaxTarget[ 0 ];
		final double z2 = targetOutOfBoundsValue;
		final double z3 = minMaxTarget[ 1 ];

		final double x1 = minMaxSource[ 0 ];
		final double x2 = sourceOutOfBoundsValue;
		final double x3 = minMaxSource[ 1 ];

		final double a2 = 255.0 / ( z3 - z1 );
		final double n2 = -a2 * z1;

		final double p = x1*x1 - x3*x3;
		final double q = x1*x1 - x2*x2;

		final double b1 =
				( (a2*z2+n2)*x1*x1*p - 255.0*x1*x1*q ) /
				( x1*x3*x3*q + x1*x1*x2*p - x1*x1*x3*q - x1*x2*x2*p );

		final double c1 = ( 255.0*x1*x1 - b1*x1*x1*x3 + b1*x1*x3*x3 ) / p;

		//final double c1b = ( (a2*z2+n2)*x1*x1 - b1*x1*x1*x2 + b1*x1*x2*x2 ) / q;

		final double a1 = ( -b1*x1 - c1 ) / ( x1*x1 );
		//final double a1b = ( 255.0 - b1*x3 - c1a ) / ( x3*x3 );
		//final double a1c = ( (a2*z2+n2) - b1*x2 - c1a ) / ( x2*x2 );

		s = "f(z) = " + a2 + "*z + " + n2 + "\n" +
			"g(x) = " + a1 + "*x*x + " + b1 + "*x + " + c1 + "\n" +
			"f(z=" + z1 + ") = " + (a2*z1 + n2) + "\n" +
			"f(z=" + z2 + ") = " + (a2*z2 + n2) + "\n" +
			"f(z=" + z3 + ") = " + (a2*z3 + n2) + "\n" +
			"f(x=" + x1 + ") = " + (a1*x1*x1 + b1*x1 + c1) + "\n" +
			"f(x=" + x2 + ") = " + (a1*x2*x2 + b1*x2 + c1) + "\n" +
			"f(x=" + x3 + ") = " + (a1*x3*x3 + b1*x3 + c1);

		for ( final FloatType t : target )
			t.set( Math.min( 255, Math.max( 0, (float)(a2*t.get() + n2) ) ) );

		for ( final FloatType t : source )
			t.set( Math.min( 255, Math.max( 0, (float)(a1*t.get()*t.get() + b1*t.get() + c1) ) ) );

		return s;
	}
	/**
	 * 
	 * @param target - the image to be transformed
	 * @param source - the image that the transformed one is matched to
	 * @param subSampling - image subsampling factor (from 0 to 7, representing 2^0=1 to 2^7 = 128)
	 * @return
	 */
	public Transformation align(
			final Img< FloatType > target,
			final Img< FloatType > source,
			final double targetOutOfBoundsValue,
			final double sourceOutOfBoundsValue,
			final int subSampling )
	{
		String s = adjustImageIntensities( target, source, targetOutOfBoundsValue, sourceOutOfBoundsValue );

		System.out.println( s );
		log += s;

		final ImagePlus targetImp = new ImagePlus( "Target", ImageTools.wrap( target ).convertToByteProcessor( false ) );
		final ImagePlus sourceImp = new ImagePlus( "Source", ImageTools.wrap( source ).convertToByteProcessor( false ) );

		targetImp.show();
		sourceImp.show();
		
		SimpleMultiThreading.threadHaltUnClean();
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
