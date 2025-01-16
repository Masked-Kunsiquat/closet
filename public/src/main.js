import React from 'react';
import ReactDOM from 'react-dom';
import App from './App';

// Register the service worker
if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        navigator.serviceWorker
        .register('/service-worker.js')
        .then((registration) => {
            console.log('Service Worker registered:', registration);
        })
        .catch((error) => {
            console.error('Service Worker registration failed:', error);
        });
    });
}

ReactDOM.render(<App />, document.getElementById('root'));