package wt.tools;

/**
 * <p>Title: Principal Curvature Plugin for ImageJ</p>
 *
 * <p>Description: Computes the Principal Curvatures of for 2D and 3D images except the pixels/voxels directly at the borders of the image</p>
 *
 * <p>Copyright: Copyright (c) 2007</p>
 *
 * <p>Company: MPI-CBG</p>
 *
 * <p>License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * @author Stephan Preibisch
 * @version 1.0
 */

import ij.process.FloatProcessor;
import Jama.EigenvalueDecomposition;
import Jama.Matrix;

public class Curvature
{
	/**
	 * This method will be called when running the PlugIn, it coordinates the main process.
	 *
	 * @author   Stephan Preibisch
	 */
	public static void compute( final FloatProcessor fp, final double sigma )
	{
		final FloatArray2D input = new FloatArray2D( (float[])fp.getPixels(), fp.getWidth(), fp.getHeight() );

		//
		// compute Gaussian
		//
		final FloatArray2D data;
		if ( sigma > 0.5 )
			data = computeGaussianFastMirror( input, (float)sigma );
		else
			data = input.clone();

		//
		// Compute Hessian Matrix and Principal curvatures for all pixels/voxels
		//
		for ( int y = 1; y < data.height - 1; ++y )
			for ( int x = 1; x < data.width - 1; ++x )
			{
				final double[][] hessianMatrix = computeHessianMatrix2D( data, x, y, sigma );
				final double[] eigenValues = computeEigenValues( hessianMatrix );

				final double v;

				// there were imaginary numbers
				if ( eigenValues == null )
					v = 0;
				else
					v = eigenValues[ 1 ];

				input.set( (float)v, x, y );
			}
	}

	public static final void setBoundaries( final FloatProcessor fp, final float value )
	{
		for ( int x = 0; x < fp.getWidth(); ++x )
		{
			fp.setf( x, 0, value );
			fp.setf( x, fp.getHeight() - 1, value );
		}

		for ( int y = 0; y < fp.getHeight(); ++y )
		{
			fp.setf( 0, y, value );
			fp.setf( fp.getWidth() - 1, y, value );
		}
	}

	/**
	 * This method computes the Eigenvalues of the Hessian Matrix,
	 * the Eigenvalues correspond to the Principal Curvatures<br>
	 * <br>
	 * Note: If the Eigenvalues contain imaginary numbers, this method will return null
	 *
	 * @param double[][] The hessian Matrix
	 * @return double[] The Real Parts of the Eigenvalues or null (if there were imganiary parts)
	 *
	 * @author   Stephan Preibisch
	 */
	public static final double[] computeEigenValues( final double[][] matrix )
	{
		final Matrix M = new Matrix(matrix);
		final EigenvalueDecomposition E = new EigenvalueDecomposition(M);

		final double[] result = E.getImagEigenvalues();

		boolean found = false;

		for (int i = 0; i < result.length; i++)
			if (result[i] > 0)
				found = true;

		if (found)
			return null;
		else
			return E.getRealEigenvalues();
	}

	/**
	 * This method computes the Hessian Matrix for the 3x3 environment of a certain pixel <br><br>
	 *
	 * The 3D Hessian Matrix:<br>
	 * xx xy <br>
	 * yx yy <br>
	 *
	 * @param img The image as FloatArray3D
	 * @param x The x-position of the voxel
	 * @param y The y-position of the voxel
	 * @return double[][] The 2D - Hessian Matrix
	 *
	 * @author   Stephan Preibisch
	 */
	public static final double[][] computeHessianMatrix2D( final FloatArray2D laPlace, final int x, final int y,final  double sigma )
	{
		final double[][] hessianMatrix = new double[2][2]; // zeile, spalte

		final double temp = 2 * laPlace.get(x, y);

		// xx
		hessianMatrix[0][0] = laPlace.get(x + 1, y) - temp + laPlace.get(x - 1, y);

		// yy
		hessianMatrix[1][1] = laPlace.get(x, y + 1) - temp + laPlace.get(x, y - 1);

		// xy
		hessianMatrix[0][1] = hessianMatrix[1][0] =
				(
						(laPlace.get(x + 1, y + 1) - laPlace.get(x - 1, y + 1)) / 2
						-
						(laPlace.get(x + 1, y - 1) - laPlace.get(x - 1, y - 1)) / 2
				) / 2;

		for (int i = 0; i < 2; i++)
			for (int j = 0; j < 2; j++)
				hessianMatrix[i][j] *= (sigma * sigma);

		return hessianMatrix;
	}
	
	public static final int max(int a, int b)
	{
		if (a > b)
			return a;
		else
			return b;
	}

	/**
	 * This method creates a gaussian kernel
	 *
	 * @param sigma Standard Derivation of the gaussian function
	 * @param normalize Normalize integral of gaussian function to 1 or not...
	 * @return float[] The gaussian kernel
	 *
	 * @author   Stephan Saalfeld
	 */
	public static float[] createGaussianKernel1D(float sigma, boolean normalize)
	{
		int size = 3;
		float[] gaussianKernel;

		if (sigma <= 0)
		{
		 gaussianKernel = new float[3];
		 gaussianKernel[1] = 1;
		}
		else
		{
		 size = max(3, (int)(2*(int)(3*sigma + 0.5)+1));

		 float two_sq_sigma = 2*sigma*sigma;
		 gaussianKernel = new float[size];

		 for (int x = size/2; x >= 0; --x)
		 {
			 float val = (float)Math.exp(-(float)(x*x)/two_sq_sigma);

			 gaussianKernel[size/2-x] = val;
			 gaussianKernel[size/2+x] = val;
		 }
	 }

	 if (normalize)
	 {
		 float sum = 0;

		 for (int i = 0; i < gaussianKernel.length; i++)
			 sum += gaussianKernel[i];

		 /*for (float value : gaussianKernel)
			 sum += value;*/

		 for (int i = 0; i < gaussianKernel.length; i++)
			 gaussianKernel[i] /= sum;
	 }


		return gaussianKernel;
	}

	/**
	 * This method does the gaussian filtering of an image. On the edges of
	 * the image it does mirror the pixels. It also uses the seperability of
	 * the gaussian convolution.
	 *
	 * @param input FloatProcessor which will be folded (will not be touched)
	 * @param sigma Standard Derivation of the gaussian function
	 * @return FloatProcessor The folded image
	 *
	 * @author   Stephan Preibisch
	 */
	public static final FloatArray2D computeGaussianFastMirror( final FloatArray2D input, final float sigma )
	{
		final FloatArray2D output = new FloatArray2D(input.width, input.height);

		float avg, kernelsum = 0;
		float[] kernel = createGaussianKernel1D(sigma, true);
		int filterSize = kernel.length;

		// get kernel sum
		/*for (double value : kernel)
			kernelsum += value;*/
		for (int i = 0; i < kernel.length; i++)
			kernelsum += kernel[i];

		// fold in x
		for (int x = 0; x < input.width; x++)
			for (int y = 0; y < input.height; y++)
				{
					avg = 0;

					if (x -filterSize / 2 >= 0 && x + filterSize / 2 < input.width)
						for (int f = -filterSize / 2; f <= filterSize / 2; f++)
							avg += input.get(x + f, y) * kernel[f + filterSize / 2];
					else
						for (int f = -filterSize / 2; f <= filterSize / 2; f++)
							avg += input.getMirror(x + f, y) * kernel[f + filterSize / 2];

					output.set(avg / kernelsum, x, y);

				}

		// fold in y
		for (int x = 0; x < input.width; x++)
			{
				float[] temp = new float[input.height];

				for (int y = 0; y < input.height; y++)
				{
					avg = 0;

					if (y -filterSize / 2 >= 0 && y + filterSize / 2 < input.height)
						for (int f = -filterSize / 2; f <= filterSize / 2; f++)
							avg += output.get(x, y + f) * kernel[f + filterSize / 2];
					 else
						for (int f = -filterSize / 2; f <= filterSize / 2; f++)
							avg += output.getMirror(x, y + f) * kernel[f + filterSize / 2];

					temp[y] = avg / kernelsum;
				}

				for (int y = 0; y < input.height; y++)
					output.set(temp[y], x, y);
			}

		return output;
	}

	/**
	 * This class is the abstract class for my FloatArrayXDs,
	 * which are a one dimensional structures with methods for access in n dimensions
	 *
	 * @author   Stephan Preibisch
	 */
	public static abstract class FloatArray
	{
		public final float data[];

		public FloatArray( final float[] data ) { this.data = data; }
		public abstract FloatArray clone();
	}

	/**
	 * The 2D implementation of the FloatArray
	 *
	 * @author   Stephan Preibisch
	 */
	public static class FloatArray2D extends FloatArray
	{
		public int width = 0;
		public int height = 0;


		public FloatArray2D( final int width, final int height )
		{
			super( new float[width * height] );
			this.width = width;
			this.height = height;
		}

		public FloatArray2D( final float[] data, final int width, final int height )
		{
			super( data );
			this.width = width;
			this.height = height;
		}

		public FloatArray2D clone()
		{
			FloatArray2D clone = new FloatArray2D( width, height );
			System.arraycopy( this.data, 0, clone.data, 0, this.data.length);
			return clone;
		}

		public int getPos( final int x, final int y )
		{
			return x + width * y;
		}

		public float get( final int x, final int y )
		{
			return data[ getPos( x, y ) ];
		}

		public float getMirror( int x, int y )
		{
			if (x >= width)
				x = width - (x - width + 2);

			if (y >= height)
				y = height - (y - height + 2);

			if (x < 0)
			{
				int tmp = 0;
				int dir = 1;

				while (x < 0)
				{
					tmp += dir;
					if (tmp == width - 1 || tmp == 0)
						dir *= -1;
					x++;
				}
				x = tmp;
			}

			if (y < 0)
			{
				int tmp = 0;
				int dir = 1;

				while (y < 0)
				{
					tmp += dir;
					if (tmp == height - 1 || tmp == 0)
						dir *= -1;
					y++;
				}
				y = tmp;
			}

			return data[getPos(x,y)];
		}

		final public void set( final float value, final int x, final int y )
		{
			data[ getPos(x,y) ] = value;
		}
	}
}
