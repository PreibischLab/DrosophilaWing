package wt;

import ij.IJ;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import mpicbg.ij.FeatureTransform;
import mpicbg.ij.SIFT;
import mpicbg.imagefeatures.Feature;
import mpicbg.imagefeatures.FloatArray2DSIFT;
import mpicbg.imagefeatures.FloatArray2DSIFT.Param;
import mpicbg.models.AbstractModel;
import mpicbg.models.AffineModel2D;
import mpicbg.models.PointMatch;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import spim.Threads;
import wt.tools.Mirror;

public class InitialTransform
{
	final static private DecimalFormat decimalFormat = new DecimalFormat();

	public InitialTransform( final Img< FloatType > template, final Img< FloatType > wing )
	{
		// Perform matching for normal and mirrored image in case the wing is flipped
		Pair< Double, List< PointMatch > > normal = computeSIFT( template, wing );
		Mirror.mirror( wing, 0, Threads.numThreads() );
		Pair< Double, List< PointMatch > > mirror = computeSIFT( template, wing );

		IJ.log( "normal: inliers=" + normal.getB().size() + ", error=" + decimalFormat.format( normal.getA() ) );
		IJ.log( "mirror: inliers=" + mirror.getB().size() + ", error=" + decimalFormat.format( mirror.getA() ) );

	}

	public Pair< Double, List< PointMatch > > computeSIFT( final Img< FloatType > template, final Img< FloatType > wing )
	{
		final InitialTransformParameters p = getParameters();
		final FloatArray2DSIFT sift = new FloatArray2DSIFT( p.siftParams );
		final SIFT ijSIFT = new SIFT( sift );

		final List< Feature > fs1 = new ArrayList< Feature >();
		final List< Feature > fs2 = new ArrayList< Feature >();

		ijSIFT.extractFeatures( Alignment.wrap( template ), fs1 );
		//IJ.log( fs1.size() + " features extracted." );

		ijSIFT.extractFeatures( Alignment.wrap( wing ), fs2 );
		//IJ.log( fs2.size() + " features extracted." );

		final List< PointMatch > candidates = new ArrayList< PointMatch >();
		FeatureTransform.matchFeatures( fs1, fs2, candidates, p.rod );

		final List< PointMatch > inliers = new ArrayList< PointMatch >();;

		//IJ.log( candidates.size() + " potentially corresponding features identified." );

		AbstractModel< ? > model = new AffineModel2D();

		boolean modelFound;
		try
		{
			modelFound = model.filterRansac(
					candidates,
					inliers,
					10000,
					p.maxEpsilon,
					p.minInlierRatio,
					p.minNumInliers );
		}
		catch ( final Exception e )
		{
			modelFound = false;
		}

		double error;

		if ( modelFound )
		{
			PointMatch.apply( inliers, model );
			error = PointMatch.meanDistance( inliers );
		}
		else
		{
			inliers.clear();
			error = Double.NaN;
		}

		return new ValuePair< Double, List< PointMatch > >( error, inliers );
	}

	protected InitialTransformParameters getParameters()
	{
		final Param siftParam = new Param();

		siftParam.initialSigma = 1.6f;
		siftParam.steps = 3;
		siftParam.minOctaveSize = 32;
		siftParam.maxOctaveSize = 1024;

		siftParam.fdSize = 4;
		siftParam.fdBins = 8;

		final InitialTransformParameters p = new InitialTransformParameters( siftParam );

		p.rod = 0.92f;
		p.maxEpsilon = 50f;
		p.minInlierRatio = 0.02f;
		p.minNumInliers = 8;

		return p;
	}

	public class InitialTransformParameters
	{
		public Param siftParams;

		public InitialTransformParameters( final Param siftParams )
		{
			this.siftParams = siftParams;
		}

		public float rod;
		public float maxEpsilon;
		public float minInlierRatio;
		public int minNumInliers;
	}
}
