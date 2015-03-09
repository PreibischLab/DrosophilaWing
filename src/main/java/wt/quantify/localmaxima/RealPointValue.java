package wt.quantify.localmaxima;

import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.Sampler;

public class RealPointValue< T > extends RealPoint implements Sampler< T >
{
	final T value;

	public RealPointValue( final T value, final int n  )
	{
		super( n );
		this.value = value;
	}

	/**
	 * Create a point at a definite location in a space of the dimensionality of
	 * the position.
	 * 
	 * @param position
	 *            the initial position. The length of the array determines the
	 *            dimensionality of the space.
	 */
	public RealPointValue( final T value, final double... position )
	{
		this( value, position, true );
	}

	/**
	 * Create a point at a definite location in a space of the dimensionality of
	 * the position.
	 * 
	 * @param position
	 *            the initial position. The length of the array determines the
	 *            dimensionality of the space.
	 */
	public RealPointValue( final T value, final float... position )
	{
		super( position.length );
		setPosition( position );
		this.value = value;
	}

	/**
	 * Create a point using the position and dimensionality of a
	 * {@link RealLocalizable}
	 * 
	 * @param localizable
	 *            the initial position. Its dimensionality determines the
	 *            dimensionality of the space.
	 */
	public RealPointValue( final T value, final RealLocalizable localizable )
	{
		super( localizable.numDimensions() );
		localizable.localize( position );
		this.value = value;
	}

	/**
	 * Protected constructor that can re-use the passed position array.
	 * 
	 * @param position
	 *            array used to store the position.
	 * @param copy
	 *            flag indicating whether position array should be duplicated.
	 */
	protected RealPointValue( final T value, final double[] position, final boolean copy )
	{
		super( copy ? position.clone() : position );
		this.value = value;
	}

	@Override
	public T get() { return value; }

	@Override
	public Sampler< T > copy() { return new RealPointValue< T >( value, position, true ); }
}