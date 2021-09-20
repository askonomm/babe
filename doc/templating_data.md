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
  "data": {
    "posts": {
      "content": "posts",
      "sortBy": "date",
      "order": "desc"
    }
  }
}
```

### Static data

As per the example, all the static data should always go into the `site` object. 

Example use in a Selmer template file:

```html
<h1>{{ site.title }}</h1>
```

### Dynamic data

As per the example, all the dynamic data should always go into the `data` object. Each item in the `data` object represents 
one singular variable creation. Like in the case of the example, it creates a variable `posts` that contains content from the folder `blog`, 
is sorted by the YAML meta key `date` in `desc` order. 

You can sort by any meta-data key, and order as "desc" or "asc". It currently gets all the files in a folder, including those in sub-folders.

Example use in a Selmer template file:
```html
{% for post in data.posts %}
    <h2><a href="/{{ post.path }}">{{ post.meta.title }}</a></h2>
    <p>{{ post.meta.date|date:"d MMM, YYYY" }}</p>
    {{ post.entry }}
{% endfor %}
```

#### Getting content

The `content` key can be a few things, such as a string or an array. Both the string an array can contain two things, such as a path to a file or a path to a folder. If it's multiple things, all of the content will be merged into one singular array as a result, but if it is just one thing it will return an object instead containing that one thing.

For example, to get a specific content item, you'd have config like this:

```json
{
  "data": {
    "single-content-item": {
      "content": "page/single-content-item.md"
    }
  }
}
```

Which will then attempt to parse the content file in the path `page/single-content-item.md`. Remember that it needs the `.md` file extension to know that it is a content file, and before you ask the answer is no, Selmer templates will not work here because they do not contain meta-data and are not content files, but rather template files.

If you want to have multiple items, simply pass an array, like this:

```json
{
  "data": {
    "team-members": {
      "content": ["team/john.md", "team/jane.md"]
    }
  }
}
```

You can also combine multiple folders of content together, like this:

```json
{
  "data": {
    "products": {
      "content": ["shop/flowers", "shop/pots"]
    }
  }
}
```

And of course, you can also combine both folders and files!

#### Sorting content

To sort content you can pass a `sortBy` into the config, which will take key of any meta-data item. So let's say your Markdown content file has a `date` meta-data item, then simply passing `date` would sort it by that!

For example:

```json
{
  "data": {
    "products": {
      "content": ["shop/flowers", "shop/pots"],
      "sortBy": "date"
    }
  }
}
```

#### Ordering content

You can order content either by descending or ascending order according to any meta key, like this:


```json
{
  "data": {
    "products": {
      "content": ["shop/flowers", "shop/pots"],
      "sortBy": "date",
      "order": "desc"
    }
  }
}
```

#### Grouping content

And, finally, you can group content by any meta-data key as well, thereby creating an object containing multiple arrays as per the grouping mechanism. 

Let's say you have a list of products and each product' content file would have a meta-data key called `category`, so to group products by category all you'd have to do is this:

```json
{
  "data": {
    "products": {
      "content": ["shop/flowers", "shop/pots"],
      "groupBy": "category"
    }
  }
}
```

##### Grouping content by date

There is a special use-case for grouping by date. In YAML meta-data all `date` meta-data items are special, in that they are converted from string into an actual date object, which allows you to do all sorts of date manipulation in a Selmer template. This also allows you to group content by any date format string you would like. For example:

```json
{
  "data": {
    "products": {
      "content": ["shop/flowers", "shop/pots"],
      "groupBy": "date|YYYY"
    }
  }
}
```

This would group all content by year. It accepts any date format that Java's DateFormatter accepts.