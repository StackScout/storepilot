module.exports = {
    'app/**/*.{ts,tsx,js,jsx,json,css}': [
        'eslint --fix',
        'prettier --write'
    ],
    'mobile/**/*.{ts,tsx,js,jsx,json,css}': [
        'eslint --fix',
        'prettier --write'
    ]
};