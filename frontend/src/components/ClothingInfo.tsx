import { Link } from "react-router-dom";

type Props = {
  name: string;
  size: string;
  price: number;
  category_id?: string;
  category_name?: string;
  brand_name?: string;
  purchase_date?: string;
};

export default function ClothingInfo({
  name,
  size,
  price,
  category_id,
  category_name,
  brand_name,
  purchase_date,
}: Props) {
  return (
    <div>
      <h2 className="mb-2 text-xl font-semibold text-gray-900 dark:text-white">{name}</h2>
      <p className="mb-4 text-xl font-extrabold text-gray-900 dark:text-white">${price.toFixed(2)}</p>

      <dl>
        <dt className="mb-2 font-semibold text-gray-900 dark:text-white">Details</dt>
        <dd className="mb-4 font-light text-gray-500 dark:text-gray-400">
          {brand_name && <span>Brand: {brand_name}, </span>}
          Size: {size}.
          {purchase_date && <span> Purchased on: {new Date(purchase_date).toLocaleDateString()}</span>}
        </dd>
      </dl>

      <dl className="flex items-center space-x-6">
        <div>
          <dt className="mb-2 font-semibold text-gray-900 dark:text-white">Category</dt>
          <dd className="mb-4 font-light text-gray-500 dark:text-gray-400">
            {category_name ? (
              <Link to={`/clothes?category=${category_id}`} className="text-blue-500 hover:underline">
                {category_name}
              </Link>
            ) : (
              "N/A"
            )}
          </dd>
        </div>
      </dl>
    </div>
  );
}
