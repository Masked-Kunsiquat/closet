import React from 'react';
import AddItemForm from './components/AddItemForm';
import ItemList from './components/ItemList';

const App = () => {
    return (
        <div>
            <h1>Closet PWA</h1>
            <AddItemForm />
            <ItemList />
        </div>
    );
};

export default App;