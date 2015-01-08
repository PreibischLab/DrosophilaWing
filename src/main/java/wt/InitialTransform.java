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
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.RigidModel2D;
import mpicbg.models.SimilarityModel2D;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import spim.Threads;
import wt.tools.Mirror;

public class InitialTransform
{
	final static private DecimalFormat decimalFormat = new DecimalFormat();
	final Img< FloatType > template, wing;
	AffineModel2D model;
	List< PointMatch > matches;
	double error;
	boolean mirror;

	public InitialTransform( final Img< FloatType > template, final Img< FloatType > wing )
	{
		this.template = template;
		this.wing = wing.copy();
	}

	public AffineModel2D model() { return model; }
	public List< PointMatch > matches() { return matches; }
	public double error() { return error; }
	public boolean mirror() { return mirror; }

	public List< PointMatch > createUpdatedMatches( final long[] offset )
	{
		final ArrayList< PointMatch > newMatches = new ArrayList< PointMatch >();
		final int n = offset.length;

		for ( final PointMatch pm : this.matches )
		{
			final float[] p1n = pm.getP1().getL().clone();
			final float[] p2n = pm.getP2().getL().clone();

			for ( int d = 0; d < n; ++d )
			{
				p1n[ d ] += offset[ d ];
				p2n[ d ] += offset[ d ];
			}

			newMatches.add( new PointMatch( new Point( p1n ), new Point( p2n ) ) );
		}

		return newMatches;
	}

	public boolean findInitialModel()
	{
		// Perform matching for normal and mirrored image in case the wing is flipped
		final Pair< Double, List< PointMatch > > normal = computeSIFT( wing, template );
		IJ.log( "normal: inliers=" + normal.getB().size() + ", error=" + decimalFormat.format( normal.getA() ) );

		Mirror.mirror( wing, 0, Threads.numThreads() );
		final Pair< Double, List< PointMatch > > mirror = computeSIFT( wing, template );
		IJ.log( "mirror: inliers=" + mirror.getB().size() + ", error=" + decimalFormat.format( mirror.getA() ) );

		final Pair< Double, List< PointMatch > > siftResult;

		if ( normal.getB().size() / ( normal.getA()/5 ) > mirror.getB().size() / ( mirror.getA()/5 ) )
		{
			siftResult = normal;
			this.mirror = false;
		}
		else
		{
			siftResult = mirror;
			this.mirror = true;
		}

		if ( siftResult.getB().size() == 0 )
		{
			IJ.log( "Initial alignment failed." );
			return false;
		}

		this.model = new AffineModel2D();

		try
		{
			this.model.fit( siftResult.getB() );
			this.matches = siftResult.getB();
			this.error = siftResult.getA();
		}
		catch ( Exception e )
		{
			this.model = null;
			this.matches = null;
			this.error = Double.NaN;
			IJ.log( "Initial alignment failed." );
			e.printStackTrace();
			return false;
		}

		return true;
	}

	protected Pair< Double, List< PointMatch > > computeSIFT( final Img< FloatType > imgA, final Img< FloatType > imgB )
	{
		final InitialTransformParameters p = getParameters();
		final FloatArray2DSIFT sift = new FloatArray2DSIFT( p.siftParams );
		final SIFT ijSIFT = new SIFT( sift );

		final List< Feature > fs1 = new ArrayList< Feature >();
		final List< Feature > fs2 = new ArrayList< Feature >();

		ijSIFT.extractFeatures( Alignment.wrap( imgA ), fs1 );
		IJ.log( fs1.size() + " features extracted." );

		ijSIFT.extractFeatures( Alignment.wrap( imgB ), fs2 );
		IJ.log( fs2.size() + " features extracted." );

		final List< PointMatch > candidates = new ArrayList< PointMatch >();
		FeatureTransform.matchFeatures( fs1, fs2, candidates, p.rod );

		final List< PointMatch > inliers = new ArrayList< PointMatch >();
		//IJ.log( candidates.size() + " potentially corresponding features identified." );

		AbstractModel< ? > model = new SimilarityModel2D();

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
			System.out.println( model );
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
		siftParam.steps = 5;
		siftParam.minOctaveSize = 32;
		siftParam.maxOctaveSize = 600;

		siftParam.fdSize = 4;
		siftParam.fdBins = 8;

		final InitialTransformParameters p = new InitialTransformParameters( siftParam );

		p.rod = 0.98f;
		p.maxEpsilon = 40f;
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
