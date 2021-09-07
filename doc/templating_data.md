# Templating data

All Selmer templates can use data available to them. This includes built-in variables as well as data you can construct yourself 
using the `babe.json` file. 

## Built-in variables

### `is_home`

Returns a boolean `true` or `false` depending on whether or not the current page is home page. 

Example use:

```html
{% if is_home %}
this will be displayed only on home page.
{% endif %}
```

### `content`

Returns the currently in-scope Markdown content file data. All Markdown content files have their meta-data available via the `meta` object, their Markdown entry available as the `entry` and their path available as `path`.

Example use:

```html
<h1><a href="/{{ content.path }}">{{ content.meta.title }}</a></h1>
<p>{{ content.meta.date|date:"d MMM, YYYY" }}</p>
{{ content.entry }}
```

## Custom data

As the nature of Babe, most of the data will be created by you using the `babe.json` file. 

An example file would look like this:

```json
{
  "site": {
    "title": "Babe Site"
  },
  "data": [
    {
      "name": "posts",
      "folder": "blog",
      "sortBy": "date",
      "order": "desc"
    }
  ]
}
```

### Static data

As per the example, all the data is static should always go into the `site` object. 

Example use in a Selmer template file:

```html
<h1>{{ site.title }}</h1>
```

### Dynamic data

As per the example, all the dynamic data should always go into the `data` array. Each item in the `data` array represents 
one singular variable creation. Like in the case of the example, it creates a variable `posts` that contains content from the folder `blog`, 
is sorted by the YAML meta key `date` in `desc` order. 

You can sort by any meta-data key, and order as "desc" or "asc". It currently gets all the files in a folder, including those in sub-folders.

**Note:** This part is still evolving and subject to change. More complex use-cases will become possible over time.

Example use in a Selmer template file:
```html
{% for post in data.posts %}
    <h2><a href="/{{ post.path }}">{{ post.meta.title }}</a></h2>
    <p>{{ post.meta.date|date:"d MMM, YYYY" }}</p>
    {{ post.entry }}
{% endfor %}
```