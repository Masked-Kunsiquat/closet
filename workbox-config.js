module.exports = {
	globDirectory: 'public/',
	globPatterns: [
		'**/*.{html,json,js,jsx}'
	],
	swDest: 'public/sw.js',
	ignoreURLParametersMatching: [
		/^utm_/,
		/^fbclid$/
	]
};